# サブプロジェクト構成の再編 — 設計

作成日: 2026-05-23

## 1. 目的

現在のトップレベル平坦な Gradle モジュール構成（`shared`, `domain`, `application`, `infrastructure`, `backend`, `frontend`）を、責務がより明確で将来の拡張に耐える階層構造へ再編する。具体的には以下を達成する。

- サーバ側コード（application / infrastructure / Ktor エントリ）を `:backend` 配下に集約する。
- kRPC 契約を独立した `:rpc` モジュールに切り出し、`:shared` の責務を「型の共有」に純化する。
- infrastructure を schema / migration の役割ごとに細分割し、マイグレーション周りに将来「アノテーション + detector」を導入できる土台を作る。

参考: [kukv/todo-app の backend 構成](https://github.com/kukv/todo-app/tree/main/backend/infrastructure)。同じ責務分担を踏襲するが、本プロジェクトは Spring Boot ではなく Ktor + Koin のため、Spring 依存は使わずプレーン JVM ライブラリ + Ktor 起動時呼び出しで実現する。

## 2. ターゲット構成

```
:domain                                            （現状維持・トップレベル）
:shared                                            （現状維持・KMP, kRPC plugin は外す）
:rpc                                       【新設】（KMP, kRPC IF + DTO のみ）
:frontend                                          （現状維持）
:backend                                           （親パス。コードは持たない）
  :backend:api                            【新設】（Ktor/Koin エントリ。現 :backend の中身を移植）
  :backend:application                             （現 :application を移動）
  :backend:infrastructure:schemas         【新設】（Exposed Table 群 = 現 infrastructure/schema/*）
  :backend:infrastructure:migration:annotation  【新設】（独自アノテーション pure JVM）
  :backend:infrastructure:migration:detector    【新設】（アノテーション付きスキーマ走査）
  :backend:infrastructure:migration:generator   【新設】（SQL 生成 = 現 MigrationGenerator）
  :backend:infrastructure:migration:executor    【新設】（Flyway 実行 = 現 MigrationRunner）
```

- トップレベルの `:application` および `:infrastructure` 名は廃止。
- 現 `:backend`（コードあり）は解体し、Ktor 実行モジュールは `:backend:api` に名前を変えて移す。
- `:domain` はトップレベル維持（将来クロスプラットフォーム化の余地を残す）。
- `:shared` から kotlinx-rpc plugin を外す（rpc 用途は `:rpc` に移譲）。当面は `Placeholder.kt` のみで中身は据え置き。

## 3. 依存方向

```
domain ←── application ←── api
   ↑           ↑            │
   │           │            ├── infrastructure:schemas ── migration:annotation
   │           │            │                                    ↑
   │           └────────────┤   infrastructure:migration:detector ┤
   │                        │           ↑
   │                        ├── migration:generator (detector + schemas + exposed-migration)
   │                        └── migration:executor  (detector + schemas + flyway)
   └── （他は依存しない）

rpc ←── api
rpc ←── frontend
shared （現時点では依存元なし。将来 frontend/api の共通型置き場として使う余地を残す）
```

ルール:
- `:domain` は他モジュールに依存しない。
- `:rpc` は `:domain` に依存しない（DTO はサーバ/クライアント中立で持つ）。
- マイグレーション系モジュールは `:domain` に依存しない。スキーマ（Exposed Table）が `:domain` の値オブジェクト型を直接参照する必要が出た場合は `:backend:infrastructure:schemas` のみが `:domain` に依存する。
- `:backend:api` がアプリ起動シーケンスで `executor` を呼んで Flyway を実行する。

## 4. モジュール責務と build.gradle.kts 方針

| モジュール | convention plugin | 主な依存 |
|---|---|---|
| `:rpc` | `kmp-shared` + `org.jetbrains.kotlin.plugin.serialization` + `org.jetbrains.kotlinx.rpc.plugin` | kotlinx-rpc-core, kotlinx-rpc-serialization-json, kotlinx-serialization-json, kotlinx-datetime |
| `:shared` | `kmp-shared`（rpc.plugin は外す） | kotlinx-coroutines-core, kotlinx-serialization-json, kotlinx-datetime |
| `:backend:api` | `ktor-server` + serialization + rpc.plugin / `application` プラグインで mainClass 指定 | `:rpc`, `:backend:application`, `:backend:infrastructure:migration:executor`, `:backend:infrastructure:schemas`, Ktor/Koin 系 |
| `:backend:application` | `kotlin-jvm` | `:domain`, kotlinx-coroutines |
| `:backend:infrastructure:schemas` | `kotlin-jvm` | `:domain`, `:backend:infrastructure:migration:annotation`, exposed-core / exposed-kotlin-datetime / exposed-jdbc |
| `:backend:infrastructure:migration:annotation` | `kotlin-jvm` | （なし） |
| `:backend:infrastructure:migration:detector` | `kotlin-jvm` | `:…:migration:annotation`, exposed-core, kotlin-reflect |
| `:backend:infrastructure:migration:generator` | `kotlin-jvm` | `:…:schemas`, `:…:migration:detector`, exposed-migration, postgres-jdbc, hikari |
| `:backend:infrastructure:migration:executor` | `kotlin-jvm` | `:…:schemas`, `:…:migration:detector`, exposed-migration, flyway-core, flyway-database-postgresql, postgres-jdbc, hikari |

Testcontainers の Docker socket / Ryuk 無効化設定（現 `infrastructure/build.gradle.kts` の `tasks.withType<Test>` ブロック）は、`build-logic` に新規 convention plugin（例: `net.brightroom.mindstock.kotlin-jvm-testcontainers`）として切り出し、`executor`（および将来 testcontainers を使う submodule）に適用する。`kotest.tags.exclude` の `manual` 既定もこの convention plugin に集約する。

## 5. ファイル移動マッピング

| 現在 | 移動先 |
|---|---|
| `application/src/**` | `backend/application/src/**` |
| `application/build.gradle.kts` | `backend/application/build.gradle.kts` |
| `backend/src/main/**`（`net.brightroom.mindstock.backend.MainKt` ほか） | `backend/api/src/main/**`（パッケージ `net.brightroom.mindstock.backend.api` にリネーム検討、最小限なら現パッケージのままでも可） |
| `backend/build.gradle.kts` | `backend/api/build.gradle.kts` |
| `infrastructure/src/main/kotlin/.../infrastructure/schema/**` | `backend/infrastructure/schemas/src/main/kotlin/.../infrastructure/schemas/**`（パッケージ末尾 `schema` → `schemas`） |
| `infrastructure/src/main/kotlin/.../infrastructure/persistence/MigrationGenerator.kt` | `backend/infrastructure/migration/generator/src/main/kotlin/.../infrastructure/migration/generator/` |
| `infrastructure/src/main/kotlin/.../infrastructure/persistence/MigrationRunner.kt` | `backend/infrastructure/migration/executor/src/main/kotlin/.../infrastructure/migration/executor/` |
| `infrastructure/src/main/resources/db/migration/**`（Flyway 用 SQL） | `backend/infrastructure/migration/executor/src/main/resources/db/migration/**` |
| `infrastructure/src/test/.../persistence/MigrationGeneratorTest.kt` | `backend/infrastructure/migration/generator/src/test/...` |
| `infrastructure/src/test/.../persistence/MigrationRunnerTest.kt` | `backend/infrastructure/migration/executor/src/test/...` |
| `infrastructure/src/test/.../persistence/GenerateInitialMigrationManually.kt` | `backend/infrastructure/migration/generator/src/test/...`（`manual` tag のまま） |
| `shared/src/commonMain/.../shared/Placeholder.kt` | 据え置き |
| `infrastructure/build.gradle.kts` の testcontainers 設定 | `build-logic` 配下の新 convention plugin に集約 |

パッケージ命名:
- `infrastructure.schema.*` → `infrastructure.schemas.*`
- `infrastructure.persistence.MigrationGenerator` → `infrastructure.migration.generator.MigrationGenerator`
- `infrastructure.persistence.MigrationRunner` → `infrastructure.migration.executor.MigrationRunner`

## 6. 今回のスコープ外（明示的に「やらない」）

- 独自アノテーションの具体設計と detector の実装。`:annotation` と `:detector` モジュールは作るが、中身は空または最小スタブにとどめ、別タスクで設計・実装する。
- `:shared` の中身の拡充。
- repository 実装の新規追加（Plan 3 以降の領域）。
- `compose.yml` で起動する Postgres スキーマ・ロール設定の変更。

## 7. 段階分け（writing-plans への入力）

各段階の終わりに `./gradlew build` と既存テスト（`MigrationGeneratorTest`, `MigrationRunnerTest`, 必要に応じ kotest `manual` タグ）を通す。

1. **`:rpc` 新設**: モジュール骨格と build.gradle.kts を追加。`:shared` から kotlinx-rpc plugin を外す。`:frontend` の rpc 依存を `:rpc` に切り替え（中身が空でもビルドは通る）。
2. **`:backend:api` 新設・`:backend:application` 新設**: 現 `:backend` を `:backend:api` にリネームしてファイルを移動。`:application` を `:backend:application` に移動。`settings.gradle.kts`、`backend/api/build.gradle.kts`、依存する `projects.application` 参照などを更新。
3. **`:backend:infrastructure:schemas` 新設**: 現 `infrastructure/src/main/kotlin/.../schema/**` を移動、パッケージ名を `schema` → `schemas` にリネーム。当面 `:annotation` への依存は宣言だけ（注釈使用なし）。
4. **`:backend:infrastructure:migration:{annotation,detector,generator,executor}` 新設**: 4 モジュールを作成し、`MigrationGenerator` / `MigrationRunner` と Flyway 用 SQL リソース、関連テストを再配置。Testcontainers 設定を `build-logic` の convention plugin に集約。`annotation` / `detector` は空または最小スタブ。
5. **後始末**: トップレベルの `application/`, `infrastructure/`, `backend/` 旧ディレクトリと旧 build.gradle.kts を削除。`settings.gradle.kts` の `include(...)` を整理。`compose.yml`・`Makefile`・README・docs（既存 `2026-05-23-mindstock-design.md` 内のモジュール構造記述）・renovate 設定・`.github/workflows` などモジュールパス参照を更新。

## 8. 検証観点

- 各段階で `./gradlew build` が通る。
- `MigrationGeneratorTest` および `MigrationRunnerTest` が引き続き green（Testcontainers が新 convention plugin 配下でも動く）。
- `./gradlew :backend:api:run`（または既存の Makefile ターゲット）でローカル起動して Flyway がマイグレーションを当て、Ktor が起動するところまで確認。
- `:frontend` の wasmJs/JS ビルドが `:rpc` 経由でコンパイル可能（クライアント側 kRPC 依存は `:rpc` に移ったあとでも従来どおり）。

## 9. 既知のリスク

- `kmp-shared` convention plugin は単独で kotlinx-rpc に依存していないため `:rpc` 用に rpc.plugin を別途付与する必要がある。`build-logic` 側で `kmp-rpc` convention を新設するかどうかは段階 1 で判断する（差分が小さければ rpc.plugin 直接適用で十分）。
- `:backend:api` のパッケージリネームを伴う場合、Ktor リソースファイル（application.yaml の mainClass 参照、Dockerfile、Compose の cmd）も連動更新が必要。最小変更ならパッケージは `net.brightroom.mindstock.backend.*` のまま据え置き、モジュール名のみ変更で済む。
- `infrastructure/build.gradle.kts` の testcontainers 設定を convention plugin に切り出す際、別モジュールに副作用が及ばないようプラグイン適用先を `executor`（および明示的に必要な submodule）に限定する。
