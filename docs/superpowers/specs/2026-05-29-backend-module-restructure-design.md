# Backend / shared module 構成見直し Design

## 1. 背景とゴール

mindstock は構築段階だが、subproject 構成に複数の違和感を抱えていた:

- `:backend:application:api` 配下に Main / configuration / usecase / RPC server / **Repository 実装** までが同居し、暫定的に層違反を許容している(`structure-review-pending` memory 参照)
- `:backend:infrastructure:migration:{annotation,detector,generator,executor}` の 4 module 自作 migration サブシステムが存在
- `:shared:rpc` と `:shared:extensions` が薄く分かれている
- `:backend:application:api` の path に "api" の兄弟が無く、`application/` 中間階層が空回りしている
- Repository interface が `:domain` に置かれており、純化されていない
- `.DS_Store` が複数 commit されている

本設計では、構築段階のうちに上記をまとめて整理する。方向性は `.tmp/library`(system-sekkei/library)の DDD パッケージ構成に寄せつつ、`domain` は外部 module として維持する。

### ゴール

- backend の subproject を `:backend:{core,api,schedules}` の 3 つに再編
- `core` を library module(application + infrastructure)、`api` を Ktor 起動 entrypoint、`schedules` を batch 用 entrypoint(スケルトン)とする
- `:domain` を純化(モデル + 型のみ)、Repository interface を `:backend:core` の `application.repository` へ移す
- 自作 migration サブシステムを Exposed 1.3.0 公式 Gradle plugin + Flyway core 直叩きで置き換え
- `:shared:{rpc,extensions}` を解体し `:shared` + `:rpc` の 2 つに再編
- testcontainers JVM lib を捨て、外部 Postgres + GHA service container ベースの統合テストへ移行
- `.DS_Store` を全削除し `.gitignore` で追跡対象から外す

### 非ゴール

- frontend module 内部の refactor(別 follow-up Plan)
- ドメインモデルそのものの再設計
- RPC service interface の API 変更
- 新機能追加

## 2. 目標 module ツリー

```
mindstock/
├── shared/              [KMP common+wasmJs+jsIR]   util。旧 :shared:extensions を吸収
├── rpc/                 [KMP common]               RPC service interface。旧 :shared:rpc を rename + 場所移動
├── domain/              [KMP common]               モデル + 型のみ(Repository interface を除外)
├── backend/
│   ├── core/            [JVM library]              application.{service,repository}, infrastructure.{datasource,transfer}, Exposed Table, Repository interface
│   ├── api/             [JVM application]          Ktor Main + configuration + presentation.rpc
│   └── schedules/       [JVM application]          Main.kt スケルトン + 最小 build.gradle.kts
└── frontend/            [KMP wasmJs+jsIR]          現状維持
```

**削除される module**: `:shared:extensions`, `:shared:rpc`, `:backend:application:api`, `:backend:infrastructure:schemas`, `:backend:infrastructure:migration:{annotation,detector,generator,executor}`(計 7)。

**module 数の変化**: 現 10 → 新 7。

## 3. `:backend:core` の内部パッケージと命名規則

```
net.brightroom.mindstock/
├── application/
│   ├── service/<ctx>/              <Ctx>Service / <Ctx>RegisterService
│   └── repository/<ctx>/           <Ctx>Repository / <Ctx>RegisterRepository(2 系統維持)
└── infrastructure/
    ├── datasource/<ctx>/           <Ctx>DataSource(Repository 実装)+ Exposed Table
    └── transfer/                   外部連携(初期は空、placeholder)
```

`<ctx>` = `catalog / household / product / stock / user`。

### 命名規則

`.tmp/library` の集約方針(参照系 / 更新系を Service クラスに集約)を採用しつつ、suffix は Repository と対称になるよう `Service` / `RegisterService` を用いる(library の `QueryService` / `RecordService` 命名は採らない)。

| 種類 | 命名 | 配置 |
|---|---|---|
| 参照系 Application Service | `<Ctx>Service` | `application.service.<ctx>/` |
| 更新系 Application Service | `<Ctx>RegisterService` | `application.service.<ctx>/` |
| Repository interface(参照) | `<Ctx>Repository` | `application.repository.<ctx>/` |
| Repository interface(更新) | `<Ctx>RegisterRepository` | `application.repository.<ctx>/` |
| Repository 実装 | `<Ctx>DataSource` | `infrastructure.datasource.<ctx>/`(1 クラスで参照 + 更新両 interface を実装) |
| Exposed Table | 現状の命名を維持 | `infrastructure.datasource.<ctx>/` |

Service と Repository が `<Ctx>` / `<Ctx>Register` の対で揃うため、参照 / 更新の責務がクラス名から一目で読める。

### Handler → Service 集約マッピング

現在 1 usecase = 1 `*Handler` クラスだが、参照系 / 更新系で各 1 クラスへ集約する。Handler のメソッド名は Service のメソッドとして残す。

| 旧 Handler | 新 Service のメソッド |
|---|---|
| `RegisterCatalogItemHandler` | `CatalogItemRegisterService.register(...)` |
| `ReviseCatalogItemHandler` | `CatalogItemRegisterService.revise(...)` |
| `SearchCatalogItemsHandler` | `CatalogItemService.search(...)` |
| `FindCatalogItemByIdHandler` | `CatalogItemService.findById(...)` |
| `CreateHouseholdHandler` | `HouseholdRegisterService.create(...)` |
| `InviteMemberHandler` | `HouseholdRegisterService.invite(...)` |
| `RevokeMembershipHandler` | `HouseholdRegisterService.revoke(...)` |
| `FindHouseholdOfUserHandler` | `HouseholdService.findOf(...)` |
| `AdoptProductHandler` | `ProductRegisterService.adopt(...)` |
| `ArchiveProductHandler` | `ProductRegisterService.archive(...)` |
| `SetMinimumStockHandler` | `ProductRegisterService.setMinimumStock(...)` |
| `FindProductHandler` | `ProductService.find(...)` |
| `ListProductsOfHouseholdHandler` | `ProductService.listOf(...)` |
| `ReplenishStockHandler` | `StockRegisterService.replenish(...)` |
| `ConsumeStockHandler` | `StockRegisterService.consume(...)` |
| `GetStockHandler` | `StockService.get(...)` |
| `ListStocksHandler` | `StockService.list(...)` |
| `GetMovementHistoryHandler` | `StockService.getMovementHistory(...)` |
| `RegisterUserHandler` | `UserRegisterService.register(...)` |
| `RenameUserHandler` | `UserRegisterService.rename(...)` |

参照系 Service が無いコンテキスト(`user`)は `UserService` を作らない。Need が出た時に追加。

## 4. `:backend:api` の内部パッケージと命名規則

```
net.brightroom.mindstock/
├── Main.kt
├── configuration/                  Ktor 固有(routing, auth, RPC plugin, error, callLogging 等)
└── presentation/
    └── rpc/<ctx>/                  <Ctx>Controller(RPC server 実装、library 流に Controller 命名)
```

### RPC server 実装の命名

`.tmp/library` の MVC Controller 命名(`LoanRegisterController`, `RetentionController` 等)を mindstock の RPC server 実装へ転用する。RPC service interface(`:rpc` 側)と RPC server 実装(`:backend:api` 側)を以下のペアで対応させる。

| `:rpc` interface | `:backend:api` 実装 |
|---|---|
| `CatalogRpcService` | `presentation.rpc.catalog.CatalogController` |
| `HouseholdRpcService` | `presentation.rpc.household.HouseholdController` |
| `ProductRpcService` | `presentation.rpc.product.ProductController` |
| `StockRpcService` | `presentation.rpc.stock.StockController` |
| `UserRpcService` | `presentation.rpc.user.UserController` |
| `UserPublicRpcService` | `presentation.rpc.user.UserPublicController` |

`configuration/migration/MigrationConfiguration.kt` は **`:backend:api` 起動時に Flyway core API で `migrate()` を呼ぶ 1 クラス**に置き換える(旧 `:backend:infrastructure:migration:executor` のロジックを圧縮)。

## 5. `:backend:schedules` の内部

```
net.brightroom.mindstock/
└── Main.kt                         空 main(起動してすぐ正常終了する程度)
```

`build.gradle.kts` は `:backend:core` 依存だけ通し、ビルドが green になる状態にする。中身は将来の batch Plan で詰める。**configuration の共通化は今後もしない** — `:backend:api` と `:backend:schedules` は entrypoint の目的が異なる(HTTP server vs batch runner)ため、共通化を狙うと無理な抽象が生まれる。重複は許容し、それぞれの configurer は独立して進化させる。

## 6. `:shared` と `:rpc`

### `:shared` [KMP common + wasmJs + jsIR]

旧 `:shared:extensions` をそのまま吸収。kotlinx datetime 拡張(`LocalDate.kt`, `LocalDateTime.kt`, `LocalTime.kt`, `TimeZone.kt` 等)。package は `net.brightroom.mindstock.extensions.kotlinx.datetime.*` を維持。

### `:rpc` [KMP common]

旧 `:shared:rpc` を rename(場所も `shared/` 配下から root へ移動)。RPC service interface(`HouseholdRpcService`, `ProductRpcService` 等)。

- package は `net.brightroom.mindstock.presentation.rpc` から `net.brightroom.mindstock.rpc` へ rename(`presentation` 配下に置きつつ shared というねじれを解消)
- 依存: `:domain` のみ

## 7. `:domain` の純化

```
net.brightroom.mindstock.domain/
├── exception/          DomainException
└── model/<ctx>/        Entity, Value Object, Aggregate
```

`domain.repository.<ctx>.*` を全て削除し `:backend:core` の `application.repository.<ctx>` へ移動する。`:rpc` は domain に依存するが、Repository interface には依存しないはず(事前 grep で確認、Phase 2 step 1 参照)。

## 8. Migration 置き換え

### Exposed Gradle plugin の適用

```kotlin
// backend/core/build.gradle.kts (Phase 2 で)
plugins {
    id("net.brightroom.mindstock.kotlin-jvm")
    id("org.jetbrains.exposed.v1.gradle.plugin") version "1.3.0"
}
exposed {
    generateMigrations {
        tablesPackage = "net.brightroom.mindstock.infrastructure.datasource"
        fileDirectory = "src/main/resources/db/migration"
        // fileVersionFormat / filePrefix / fileSeparator は plugin default に任せる
    }
}
```

**Phase 1 では `:backend:infrastructure:schemas` に同 plugin を適用**(構造変更前に migration 置き換えを先行させる)し、`tablesPackage` を現状の schemas package に向ける。

### 既存 SQL の扱い

| ファイル | 扱い |
|---|---|
| `V20260523000001__append_only_role.sql` | role 作成、Exposed plugin の管轄外。**ファイル名を `V00000000000000__append_only_role.sql` にリネームして必ず最初に走る baseline 扱いとする**(Flyway は文字列比較で version 順に流すため、ゼロ埋めで最古に固定)。中身はそのまま |
| `V20260523071825__init.sql` | 初期 schema、Exposed plugin が再生成可能。**Phase 1 で削除し、plugin で生成し直す**(新しい timestamp で再生成される) |

mindstock は構築段階でどこにも deploy していないため、連番の連続性は問題にしない。生成 SQL のファイル名形式が変わっても許容する。

### ランタイム適用

`:backend:api` と `:backend:schedules` がそれぞれ起動時に Flyway core API(`flyway-core`)で `Flyway.configure().dataSource(...).locations("classpath:db/migration").load().migrate()` を呼ぶ。

- `:backend:core` の resources/db/migration が classpath 経由で見える
- `:backend:api` / `:backend:schedules` は `flyway-core` + `flyway-database-postgresql` の依存を持つ
- DataSource は各 entrypoint の DI 設定で生成(core には DataSource を置かない)

## 9. Test infrastructure 入れ替え

### 方針

testcontainers JVM lib (`libs.testcontainers.postgres`, `libs.testcontainers.junit`)を全削除。テストは外部 Postgres(`TEST_DB_URL`)に接続するだけのクライアントになる。

| 環境 | Postgres の提供元 |
|---|---|
| local dev | `docker compose up -d postgres-test`(`compose.yml` に test 用 service を追加) |
| GHA | workflow の `services:` で Postgres を起動 |

### task 構成

`:backend:core` に `java-test-fixtures` を適用し、testFixtures に「DataSource ファクトリ + テスト前 cleanup(全テーブル TRUNCATE)」を置く。`:backend:api` / `:backend:schedules` のテストから参照可能にする。

- JUnit 5 `@Tag("integration")` を統合テストに付与
- `./gradlew test` は `excludeTags("integration")` で **unit のみ**(高速)
- `./gradlew integrationTest` を register し、`includeTags("integration")` で **統合テストのみ**実行
- `./gradlew check` の依存は `test + integrationTest`
- `TEST_DB_URL` が未設定 / 接続失敗の場合、`integrationTest` は明示的に **fail**(skip しない、CI で見落とさないように)
- 各 test class で schema 分離(`SET search_path` or `CREATE SCHEMA test_<random>`)を必要に応じて入れる

### GHA workflow

既存 workflow の `services:` に Postgres を追加し、`env.TEST_DB_URL` を渡す。AUTH_* の placeholder と同じ仕組み(`d185377` の経験を反映)。

## 10. 影響範囲

旧 `domain.repository.*` の参照点を Phase 2 step 1 で grep して全列挙し、Phase 2 step 3 までに全 import を新パッケージに書き換える。想定される依存元:

- `:backend:core` の `application/service/<ctx>/*Handler`(現 `:backend:application:api` の usecase)
- `:backend:core` の `infrastructure/datasource/<ctx>/*RepositoryImpl`(現 application/api の repo impl)
- `:rpc` の RPC service interface(通常依存しない想定だが要確認)
- `:domain` の test(`SerializationRoundTripTest` 等、要確認)
- `:frontend`(通常依存しない想定だが要確認)

## 11. Phase 分割

**1 つの Plan に統合するが、コミット単位で 2 phase に強制的に分ける**。レビュー粒度を保ち、Phase 1 完了時点でビルドが green な中間状態を作る。

### Phase 1: Exposed bump + migration:* 置き換え(構造は現状維持)

1. Exposed `1.0.0-beta-4` → `1.3.0`(`gradle/libs.versions.toml`)
2. `org.jetbrains.exposed.v1.gradle.plugin` 1.3.0 を `:backend:infrastructure:schemas` に適用、`tablesPackage` を現 schemas package に
3. 既存 `V20260523071825__init.sql` 削除、`generateMigrations` で生成し直す
4. `V20260523000001__append_only_role.sql` を `V00000000000000__append_only_role.sql` にリネーム(baseline として最古固定)
5. `:backend:application:api` の `configuration/migration/MigrationConfiguration.kt` を Flyway core 直叩き版に置き換え(executor の依存を切る)
6. `:backend:infrastructure:migration:{annotation,detector,generator,executor}` を settings から削除 + ディレクトリ削除
7. `./gradlew build` + `./gradlew :backend:application:api:run` で動作確認
8. **コミット**(`refactor(backend): replace home-grown migration with Exposed Gradle plugin`)

### Phase 2: module 再編 + package 移動 + test infra 入れ替え

1. `rg "domain.repository"` で全参照を列挙、影響範囲リストを spec(本書)に追記 or PR description に貼る
2. `:backend:core` を新設(`backend/core/build.gradle.kts`)、`:backend:api` を新設(`backend/api/build.gradle.kts`、`:backend:core` 依存)、`:backend:schedules` をスケルトンで新設
3. `settings.gradle.kts` から `:backend:application:api`, `:backend:infrastructure:schemas` を削除、新 module を include
4. Repository interface を `:domain/domain/repository/*` → `:backend:core/application/repository/<ctx>/*` へ移動
5. Exposed Table を `:backend:infrastructure:schemas` → `:backend:core/infrastructure/datasource/<ctx>/*` へ移動(Repository 実装と同居)
6. `*Handler` 群を `:backend:core/application/service/<ctx>` 配下の `<Ctx>QueryService` / `<Ctx>RecordService` に集約 rename(§3 マッピング表に従う)
7. RPC server 実装を旧 `application/api/.../rpc/<ctx>` → `:backend:api/presentation/rpc/<ctx>/<Ctx>Controller` に rename + 移動(§4 マッピング表に従う)
8. `:backend:api` に Exposed Gradle plugin の適用先を移動(Phase 1 で schemas に置いたものを core に移す。core は library module なので plugin 適用先は core)
9. `:shared:rpc` 削除 → `:rpc` 新設、package rename(`presentation.rpc` → `rpc`)、全参照を更新
10. `:shared:extensions` 削除 → `:shared` 新設、内容をそのまま移植
11. testcontainers JVM lib 削除、`TEST_DB_URL` ベースの統合テスト helper を `:backend:core` の testFixtures に作る
12. `@Tag("integration")` を全統合テストに付与、`integrationTest` task を register
13. `compose.yml` に `postgres-test` service を追加(本番 Postgres とポート分離)
14. GHA workflow に Postgres `services:` 追加 + `TEST_DB_URL` 環境変数
15. `.DS_Store` 全削除(`find . -name .DS_Store -not -path './.git/*' -delete`)、`.gitignore` に追記
16. `./gradlew check` + `./gradlew :backend:api:run` で動作確認
17. **コミット**(粒度に応じて複数に分けてよい)

## 12. 既知のリスクと許容

- **Exposed plugin の `generateMigrations` は build-time に testcontainers で Postgres を立てて diff を取る** — つまり dev ローカルで migration を作る時は Docker 必須。普段の test 実行(unit / integration)では不要。CI で `generateMigrations` は流さない(commit された SQL を信頼)
- **Phase 1 で `:backend:infrastructure:schemas` に plugin を適用、Phase 2 で `:backend:core` に移す** — 同じ plugin が一度引っ越す形になるが、Phase 1 完了時点で動作確認できる状態にする方を優先する
- **`:backend:api` と `:backend:schedules` の `configuration/` が重複する** — 重複を許容方針とする。両 entrypoint は目的(HTTP server vs batch runner)が異なり、共通化は無理な抽象を生む。再評価しない
- **生成 SQL の連番不連続** — 構築段階でどこにも deploy していないため許容

## 13. 後続(memory に残すべき内容)

- frontend auth refactor(`frontend-auth-refactor-followup`)は引き続き別 Plan
