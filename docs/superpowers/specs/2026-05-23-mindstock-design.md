# mindstock 設計ドキュメント

- 作成日: 2026-05-23
- 対象: MVP

## 1. プロダクト概要

日用品の在庫管理アプリ。買い忘れの解消と買い過ぎの抑制を目的とする個人/家族向けの PWA。

### 1.1 解決したい問題

- 外出前に何を買うべきか把握できず、買い忘れる
- 外出後に「あれ買っとけばよかった」と気づく

### 1.2 MVP のスコープ

1. 商品の購入登録(補充)
2. 商品の消費登録
3. 現在の在庫一覧表示
4. 買い物リスト表示(在庫が閾値以下の商品)

将来スコープ(MVP に含めない): 在庫減少時の通知、購入時期予測、Yahoo!ショッピング / 楽天商品検索 API 連携、複数世帯間共有の本格運用。

### 1.3 利用者像

- 個人ユーザー(自分のみ)が当面のメイン
- 将来的に家族(同一世帯)が共有して使う想定
- 世帯間のデータ共有(=複数世帯のメンバーが同じ世帯に所属)は想定するが、MVP では実質 1 ユーザー 1 世帯運用

## 2. 設計の柱

| 柱 | 内容 |
|---|---|
| ドメイン駆動設計 (DDD) | 集約・イベント・値オブジェクトを明確に分離する |
| Append-only な永続化 | UPDATE/DELETE は原則禁止。各テーブルが「事実の積み重ね」を表現する |
| イベントソーシング的思想 | 中央 `events` テーブルではなく、各概念ごとに独立した事実テーブルを持つ |
| 共有商品カタログ | 商品マスタは全世帯で共有(将来の外部 API 連携の素地) |
| Kotlin で frontend / backend を統一 | 学習目的も含め、shared モジュールで型と契約を共有 |

## 3. アーキテクチャ

### 3.1 全体構成

```
[Frontend: Compose Multiplatform (Wasm/PWA)]
   │ kotlinx-rpc (WebSocket) + Zitadel OIDC Bearer
   ▼
[Backend: Ktor]
   ├ kotlinx-rpc Service impl
   ├ Application (UseCase)
   ├ Domain (Aggregate, Event, Policy)
   └ Infrastructure (Exposed, Auth, OTel)
   │ JDBC
   ▼
[PostgreSQL 18]
   - 事実テーブル群(append-only)

[Zitadel] ← OIDC 認証のみ。認可・世帯メンバーシップはアプリ側で管理
```

### 3.2 Gradle マルチプロジェクト構成

- Gradle 9.5、Kotlin 2.x(K2 コンパイラ)、AGP は不使用(Android ターゲットなし)
- `build-logic` の composite build で convention plugin を提供
- Version Catalog(`gradle/libs.versions.toml`)で依存を一元管理
- Type-safe project accessors(`projects.shared` 等)を活用

```
mindstock/
├── settings.gradle.kts
├── build.gradle.kts                # 集約のみ
├── gradle/
│   └── libs.versions.toml
├── build-logic/                    # composite build
│   ├── settings.gradle.kts
│   └── src/main/kotlin/
│       ├── net.brightroom.mindstock.kotlin-jvm.gradle.kts
│       ├── net.brightroom.mindstock.kotlin-jvm-testcontainers.gradle.kts
│       ├── net.brightroom.mindstock.kmp-shared.gradle.kts
│       ├── net.brightroom.mindstock.ktor-server.gradle.kts
│       ├── net.brightroom.mindstock.compose-web.gradle.kts
│       └── net.brightroom.mindstock.spotless.gradle.kts
├── shared/                         # KMP: JVM + JS + Wasm。クライアント/サーバ共有モジュール群
│   ├── rpc/                        #   kotlinx-rpc サービス IF + DTO(presentation.rpc)
│   └── extensions/                 #   kotlinx-datetime / serialization の拡張(@js-joda/timezone を wasm に bundle)
├── domain/                         # JVM only。Aggregate, Event, Policy(永続化非依存)
├── backend/
│   ├── application/
│   │   └── api/                    # JVM only。Ktor server entrypoint + use case 配線
│   │                               # (use case はまだ存在しないため、application 配下に api 統合)
│   └── infrastructure/
│       ├── schemas/                # JVM only。Exposed Table 定義(infrastructure.datasource.schemas.*)
│       └── migration/
│           ├── annotation/         # @Migratable アノテーション
│           ├── detector/           # スキーマ差分検出(現状は手動レジストリ)
│           ├── generator/          # SQL 生成(MigrationGenerator)
│           └── executor/           # Flyway 実行(MigrationRunner)
└── frontend/                       # CMP Wasm。UI Composables + state + RPC client
```

依存方向:

```
frontend             ──> shared:rpc
backend:application:api ──> shared:rpc
backend:application:api ──> shared:extensions
backend:application:api ──> domain
backend:application:api ──> backend:infrastructure:schemas
backend:application:api ──> backend:infrastructure:migration:executor

backend:infrastructure:schemas       ──> domain + migration:annotation
backend:infrastructure:migration:detector  ──> schemas + migration:annotation
backend:infrastructure:migration:generator ──> schemas + migration:detector
backend:infrastructure:migration:executor  ──> schemas + migration:detector
```

`domain` は何にも依存しない純粋 Kotlin。`shared:rpc` は kRPC サービス IF + DTO のみ。`shared:extensions` は kotlinx-datetime / serialization の拡張(`TimeZone.JST`, `LocalDate.now(...)`, `CustomJson` 等)。

## 3.3 Ktor アプリケーション構成

`backend:application:api` の entry point は `net.brightroom.mindstock.MainKt`(`fun main(args)` で `EngineMain.main(args)` を呼ぶだけ)。Ktor module 関数は `application.yaml` の `ktor.application.modules` で個別に列挙する(Application.module() の集約は使わず、per-concern に分離した拡張関数を直接 modules として登録する)。

各構成は `configuration` パッケージ下に per-concern で分離:

- `configuration.Environment` — `LOCAL / DEV / STG / PROD` 列挙
- `configuration.di.DependenciesConfiguration` — Ktor DI への登録(プレースホルダ)
- `configuration.migration.MigrationConfiguration` — 一時的な Hikari プールで Flyway を実行し、適用後にプールを閉じる(runtime プールと migration プールを分離)
- `configuration.external.exposed.{ExposedDataSourceProperties, ExposedConfiguration}` — runtime 用 Hikari + Exposed 接続を `provide<Database>` で DI 登録(`useNestedTransactions = true`)
- `configuration.logging.LoggingConfiguration` — CallId / CallLogging / DoubleReceive、本番では `receiveText` を除外
- `configuration.routing.RoutingConfiguration` — ContentNegotiation(`jsonIo(CustomJson)`) と routing

`@Property("external.datasource.database")` で `ExposedDataSourceProperties` を yaml の `external.datasource.database.*` 配下にバインド。Environment は `@Property("ktor.environment")` で yaml の `ktor.environment` にバインド。

modules の実行順序(`application.yaml`):

```
1. dependenciesConfigure
2. migrationConfigure       # Flyway を先に適用
3. exposedConfigure         # runtime Database を DI 登録
4. loggingConfigure
5. routingConfigure
```

## 4. ドメインモデル

### 4.1 集約

| 集約 | 役割 |
|---|---|
| User | アプリ内ユーザー。Zitadel sub を識別子とする |
| Household | 世帯。所有者と将来のメンバーを束ねる |
| CatalogItem | 全世帯で共有される商品概念(名前・単位) |
| Product | 世帯固有の商品インスタンス(CatalogItem を採用したもの)+ 最低在庫設定 |
| Stock | 商品ごとの補充/消費イベントの集合 |

### 4.2 「事実テーブル」の設計原則

- 各テーブルは append-only(INSERT のみ)
- 「変わり得る属性」は独立した事実テーブル(`product_minimum_stocks` 等)
- 集約ルート(エンティティそのもの)の存在も 1 行で表現(`products` テーブル等)
- 訂正・取り消しも「訂正された事実」として新規 INSERT で表現
- アーカイブ・削除も「アーカイブされた事実」テーブルで表現(物理削除は行わない)

### 4.3 ID 戦略

- **集約ルートの ID**: `UUID v7`(PostgreSQL 18 ネイティブ `uuidv7()`)。時系列順に並び、index 効率が良い
- **履歴テーブルの ID**: `BIGINT GENERATED ALWAYS AS IDENTITY`。「id 降順 = 最新」が単調保証される
- アプリ側で生成する場合は ULID/UUIDv7 のライブラリを使う

### 4.4 不変条件

- `quantity > 0`(増減方向はイベント種別で表現、数量自体は常に正)
- `minimum_stock >= 0`
- `occurred_at <= created_at`(occurred_at は過去日入力可、未来日は不可)
- 在庫が 0 以下になる消費も**許容**する(現実との乖離は補正イベントで合わせる)
- アーカイブされた商品には新規 stock イベント発火禁止
- 同じ catalog_item を同一世帯で重複採用しない(`UNIQUE (household_id, catalog_item_id)`)
- 訂正は同一 productId 配下のイベントのみ対象

### 4.5 訂正の方針

「3 個消費」と入力したが正しくは「2 個」だったケース:

- 対応する訂正テーブルに INSERT(該当 FK + `corrected_quantity` + `reason` + `corrected_by`)
  - 補充イベントの訂正: `stock_replenishment_corrections`(`stock_replenishment_id → stock_replenishments(id)`)
  - 消費イベントの訂正: `stock_consumption_corrections`(`stock_consumption_id → stock_consumptions(id)`)
- 元イベントはそのまま残る
- 読み取り側(`effective_stock_*` クエリ)で訂正があれば最新の `corrected_quantity` を採用する
- `reason` は NOT NULL — 「何故訂正したか」をドメインの一級事実として残す
- ポリモーフィック関連(`target_table` ディスクリミネータ)は避け、FK 制約を効かせる

## 5. データベーススキーマ

PostgreSQL 18 を前提とする。

### 5.1 User ドメイン

```sql
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT uuidv7(),
    zitadel_sub   TEXT NOT NULL UNIQUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_display_names (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       UUID NOT NULL REFERENCES users(id),
    display_name  TEXT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### 5.2 Household ドメイン

```sql
CREATE TABLE households (
    id            UUID PRIMARY KEY DEFAULT uuidv7(),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE household_memberships (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    household_id  UUID NOT NULL REFERENCES households(id),
    user_id       UUID NOT NULL REFERENCES users(id),
    role          TEXT NOT NULL,                                  -- 'owner' | 'member'
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE household_membership_revocations (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    membership_id   BIGINT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### 5.3 Catalog ドメイン(全世帯で共有)

```sql
CREATE TABLE catalog_items (
    id            UUID PRIMARY KEY DEFAULT uuidv7(),
    created_by    UUID NOT NULL REFERENCES users(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE catalog_item_names (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    catalog_item_id UUID NOT NULL REFERENCES catalog_items(id),
    name            TEXT NOT NULL,
    edited_by       UUID NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE catalog_item_units (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    catalog_item_id UUID NOT NULL REFERENCES catalog_items(id),
    unit            TEXT NOT NULL,
    edited_by       UUID NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

MVP のカタログ編集ポリシー: 誰でも追加・編集可。`edited_by` を残しているので将来的に履歴監査・ロールバック(=新規行 INSERT で復元)が可能。

### 5.4 Product ドメイン(世帯固有)

```sql
CREATE TABLE products (
    id                UUID PRIMARY KEY DEFAULT uuidv7(),
    household_id      UUID NOT NULL REFERENCES households(id),
    catalog_item_id   UUID NOT NULL REFERENCES catalog_items(id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (household_id, catalog_item_id)
);

CREATE TABLE product_minimum_stocks (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id      UUID NOT NULL REFERENCES products(id),
    minimum_stock   INT NOT NULL CHECK (minimum_stock >= 0),
    edited_by       UUID NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE product_archives (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id    UUID NOT NULL REFERENCES products(id),
    archived_by   UUID NOT NULL REFERENCES users(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

商品の名前・単位は CatalogItem 由来。世帯固有の名前オーバーライドは MVP では持たない。

### 5.5 Stock ドメイン

```sql
CREATE TABLE stock_replenishments (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id      UUID NOT NULL REFERENCES products(id),
    quantity        INT NOT NULL CHECK (quantity > 0),
    occurred_at     TIMESTAMPTZ NOT NULL,
    acted_by        UUID NOT NULL REFERENCES users(id),
    note            TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE stock_consumptions (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id      UUID NOT NULL REFERENCES products(id),
    quantity        INT NOT NULL CHECK (quantity > 0),
    occurred_at     TIMESTAMPTZ NOT NULL,
    acted_by        UUID NOT NULL REFERENCES users(id),
    note            TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE stock_replenishment_corrections (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    stock_replenishment_id  BIGINT NOT NULL REFERENCES stock_replenishments(id),
    corrected_quantity      INT NOT NULL CHECK (corrected_quantity > 0),
    reason                  TEXT NOT NULL,
    corrected_by            UUID NOT NULL REFERENCES users(id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE stock_consumption_corrections (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    stock_consumption_id    BIGINT NOT NULL REFERENCES stock_consumptions(id),
    corrected_quantity      INT NOT NULL CHECK (corrected_quantity > 0),
    reason                  TEXT NOT NULL,
    corrected_by            UUID NOT NULL REFERENCES users(id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### 5.6 Append-only の保証

```sql
GRANT SELECT, INSERT ON ALL TABLES IN SCHEMA public TO mindstock_app;
-- UPDATE, DELETE は明示的に付与しない
```

例外的に UPDATE/DELETE が必要になる場合(例: 個人情報の削除要求対応)は、別ロールで実行し、運用ログを残す。

### 5.7 読み取り戦略

SQL VIEW は使わず、Exposed の `withDistinctOn()` + `QueryAlias` で「最新値の集約」を Kotlin コードとして表現する(参考: 過去プロジェクト [kukv/todo-app](https://github.com/kukv/todo-app))。

例: 「カタログ商品の最新名前」のクエリエイリアス

```kotlin
val LatestCatalogItemNames: QueryAlias
    get() = CatalogItemNamesTable
        .selectAll()
        .withDistinctOn(CatalogItemNamesTable.catalog_item_id)
        .orderBy(
            CatalogItemNamesTable.catalog_item_id to SortOrder.ASC,
            CatalogItemNamesTable.id to SortOrder.DESC,
        )
        .alias("latest_catalog_item_names")
```

`current_products`, `current_stock`, `shopping_list` といった読み取り API は、これらのエイリアスを join して構築する Repository メソッドとして実装する。

## 6. API 契約(kotlinx-rpc)

### 6.1 サービスインターフェース

`:shared` モジュールに `@Rpc` インターフェースを定義し、frontend / backend で参照する。

```kotlin
@Rpc
interface InventoryService : RemoteService {

    // Catalog
    suspend fun registerCatalogItem(cmd: RegisterCatalogItem): CatalogItemId
    suspend fun renameCatalogItem(cmd: RenameCatalogItem)
    suspend fun changeCatalogItemUnit(cmd: ChangeCatalogItemUnit)
    suspend fun searchCatalogItems(query: String): List<CatalogItemView>

    // Product
    suspend fun adoptProduct(cmd: AdoptProduct): ProductId
    suspend fun setMinimumStock(cmd: SetMinimumStock)
    suspend fun archiveProduct(cmd: ArchiveProduct)

    // Stock
    suspend fun replenishStock(cmd: ReplenishStock): StockEventId
    suspend fun consumeStock(cmd: ConsumeStock): StockEventId
    suspend fun correctStockEvent(cmd: CorrectStockEvent)

    // Queries
    suspend fun currentStock(): List<StockView>
    suspend fun shoppingList(): List<ShoppingListItem>
    suspend fun stockHistory(productId: ProductId, limit: Int = 50): List<StockHistoryEntry>

    // Me
    suspend fun me(): MeView
    suspend fun setDisplayName(cmd: SetDisplayName)
}
```

### 6.2 値オブジェクト・DTO

`@Serializable @JvmInline value class` で型安全な ID を導入する。

```kotlin
@Serializable @JvmInline value class CatalogItemId(val value: String)   // UUIDv7 文字列
@Serializable @JvmInline value class ProductId(val value: String)
@Serializable @JvmInline value class HouseholdId(val value: String)
@Serializable @JvmInline value class UserId(val value: String)
@Serializable @JvmInline value class StockEventId(val value: Long)
```

コマンド DTO(主要):

```kotlin
@Serializable data class RegisterCatalogItem(val name: String, val unit: String)
@Serializable data class RenameCatalogItem(val catalogItemId: CatalogItemId, val newName: String)
@Serializable data class ChangeCatalogItemUnit(val catalogItemId: CatalogItemId, val newUnit: String)

@Serializable data class AdoptProduct(val catalogItemId: CatalogItemId, val minimumStock: Int? = null)
@Serializable data class SetMinimumStock(val productId: ProductId, val minimumStock: Int)
@Serializable data class ArchiveProduct(val productId: ProductId)

@Serializable data class ReplenishStock(
    val productId: ProductId, val quantity: Int,
    val occurredAt: Instant, val note: String? = null,
)
@Serializable data class ConsumeStock(
    val productId: ProductId, val quantity: Int,
    val occurredAt: Instant, val note: String? = null,
)
@Serializable enum class StockEventTargetKind { Replenishment, Consumption }
@Serializable data class StockEventTarget(val kind: StockEventTargetKind, val id: StockEventId)
@Serializable data class CorrectStockEvent(
    val target: StockEventTarget, val newQuantity: Int, val reason: String = "",
)

@Serializable data class SetDisplayName(val displayName: String)
```

クエリ DTO(主要):

```kotlin
@Serializable data class CatalogItemView(
    val catalogItemId: CatalogItemId, val name: String, val unit: String,
)
@Serializable data class StockView(
    val productId: ProductId, val catalogItemId: CatalogItemId,
    val name: String, val unit: String,
    val quantity: Int, val minimumStock: Int?,
)
@Serializable data class ShoppingListItem(
    val productId: ProductId, val name: String, val unit: String,
    val currentQuantity: Int, val minimumStock: Int, val shortage: Int,
)
@Serializable data class StockHistoryEntry(
    val id: StockEventId, val kind: StockEventTargetKind,
    val quantity: Int,                                    // 訂正適用後
    val occurredAt: Instant, val recordedAt: Instant,
    val actedBy: UserId, val actedByDisplayName: String,
    val note: String?, val corrected: Boolean,
)
@Serializable data class MeView(
    val userId: UserId, val displayName: String, val householdId: HouseholdId,
)
```

### 6.3 エラー

`@Serializable sealed class InventoryException` を定義し、kotlinx-rpc の例外伝播で frontend に返す。

- `CatalogItemNotFound`
- `DuplicateAdoption`
- `ProductArchived`
- `StockEventNotFound`
- `Forbidden`

### 6.4 コマンドフロー(consumeStock の例)

1. Frontend がコマンドを生成して RPC 呼び出し(JWT を Bearer で付与)
2. Backend の AuthFilter が JWT を検証(Zitadel JWKS)、Zitadel sub から `users.id` を解決(初回は users へ INSERT)
3. UserContext を coroutine context に注入
4. `InventoryServiceImpl.consumeStock` → `ConsumeStockUseCase`
5. UserContext から household_id を解決(`household_memberships` の最新)
6. 認可: product が当該世帯のものか確認
7. 不変条件: product が archived でない、quantity > 0
8. `stock_consumptions` に INSERT(RETURNING id)
9. `StockEventId` を返す

書き込みは 1 コマンド 1 トランザクション。append のみなので軽い。

## 7. 認証・認可

### 7.1 認証

- Zitadel を OIDC プロバイダとして利用
- frontend は認可コード + PKCE フローで access token / id token を取得
- access token を Bearer で kotlinx-rpc 経由 backend に渡す
- backend は Zitadel の JWKS で JWT 検証

### 7.2 認可

- アプリ側で完結。Zitadel には認証(誰か)のみを担わせる(Zitadel の責務肥大化を避ける)
- 認可ロジックは Household ドメイン側で管理(`household_memberships` で誰がどの世帯に所属するかを表現)
- 認可判定は VIEW 相当のクエリ(`latest_household_memberships` 等)で実行

## 8. Frontend

- Compose Multiplatform(Wasm/JS ターゲット)で PWA を構築
- 状態管理: ViewModel + StateFlow
- ナビゲーション: Compose Navigation(Multiplatform 対応版)
- RPC クライアント: kotlinx-rpc(WebSocket トランスポート)
- 認証: OIDC ライブラリ(`oidc-client-ts` 相当の JS 連携、または KMP 対応 OIDC ライブラリ)

### 8.1 オフライン挙動

- **読み取りのみオフライン対応**(書き込みはオンライン必須)
- Service Worker で `currentStock` / `shoppingList` のレスポンスをキャッシュ
- 書き込み操作はオフライン時は無効化 UI(再試行不可)
- iOS Safari でもホーム画面追加で実用可能(iOS 16.4+ で SW・Push 対応)

### 8.2 主要画面(MVP)

1. ログイン(Zitadel リダイレクト)
2. 在庫一覧(`current_stock` 表示、商品ごとに「補充」「消費」ボタン)
3. 補充ダイアログ(数量 + occurredAt)
4. 消費ダイアログ(数量 + occurredAt)
5. 商品登録(カタログ検索 → 採用 + 閾値設定)
6. 商品設定(閾値変更、アーカイブ)
7. 買い物リスト(`shopping_list` 表示)
8. 履歴(商品単位の `stockHistory`)+ 訂正 UI
9. プロフィール(表示名設定)

## 9. インフラ・基盤

### 9.1 マイグレーション

- Exposed のスキーマ定義(`Table` オブジェクト)に独自 `@Migratable` アノテーションを付与
- Gradle タスク or CLI で `MigrationUtils.generateMigrationScript()` を実行し、Flyway 互換の SQL ファイル(`V{timestamp}__description.sql`)を生成
- アプリ起動時に Flyway が `migration/` ディレクトリの SQL を適用
- VIEW は使わないので、マイグレーションは `CREATE TABLE` と index 中心
- スキーマ定義(`Table`)は `backend/infrastructure/schemas/`(パッケージ `net.brightroom.mindstock.infrastructure.datasource.schemas.*`)に集約

### 9.2 DB アクセス

- Exposed JDBC(`org.jetbrains.exposed.v1.jdbc`)+ HikariCP
- DAO は使わず DSL モードに統一(append-only に親和)
- coroutines との結合は `Dispatchers.IO` で実行
- 「最新値の集約」は `withDistinctOn()` + `QueryAlias` で表現

### 9.3 観測性(OpenTelemetry)

既存の OpenTelemetry 基盤(メトリクス・ログ・トレース)に接続する。

| 種別 | 実装 |
|---|---|
| メトリクス | Micrometer の OTel registry + OTLP exporter。Ktor リクエスト、JVM、Exposed クエリ、ドメインメトリクス(コマンド数・補正回数) |
| トレース | OTel Java Agent で JDBC・Ktor を自動計装。ドメイン UseCase に手動 Span |
| ログ | Logback OTel appender(`OpenTelemetryAppender`)で OTLP 送信。traceId/spanId を MDC で構造化 JSON ログにも残す |
| 設定 | `OTEL_*` 環境変数(OTel 標準)で受ける |

依存(主要):

- `io.opentelemetry:opentelemetry-api`
- `io.opentelemetry:opentelemetry-sdk`
- `io.opentelemetry:opentelemetry-exporter-otlp`
- `io.opentelemetry.instrumentation:opentelemetry-ktor-3.0`
- `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0`
- `io.micrometer:micrometer-registry-otlp`

**未確定事項**: kotlinx-rpc(experimental)が trace context 伝播の拡張ポイントを提供しているか要調査。提供されていない場合、当面は backend 内部のスパン繋がりのみとし、frontend → backend のトレースリンクは WebSocket ハンドシェイク時の HTTP ヘッダ `traceparent` で代用する。

### 9.4 ローカル開発

- `compose.yml` で PostgreSQL 18 + Zitadel をローカル起動
- backend: `./gradlew :backend:application:api:run`
- frontend: `./gradlew :frontend:wasmJsBrowserDevelopmentRun`
- frontend dev server から backend へプロキシ設定(CORS 回避)

### 9.5 CI(GitHub Actions)

- Lint: Spotless + ktlint(`*.kt` と `*.kts` 両方、ktlint デフォルト設定)。`./gradlew spotlessCheck` を CI で実行、失敗時はビルド落とす
- Build: `./gradlew build`(全モジュール)
- Test: ユニット + Testcontainers ベース統合テスト
- Frontend distribution: `./gradlew :frontend:wasmJsBrowserDistribution`
- 将来: コンテナイメージビルド・push

### 9.6 テスト戦略

| 層 | テスト | ツール |
|---|---|---|
| domain | 集約・不変条件・ポリシーの単体テスト | Kotest |
| backend:application:api (use case) | UseCase をモック Repository でテスト | Kotest + MockK |
| backend:infrastructure:* | **Testcontainers で本物の PostgreSQL 18** | Testcontainers(testFixtures で `TestContainersPostgres` を `executor` から共有) |
| backend:application:api | kotlinx-rpc in-memory transport で E2E | Ktor TestApplication |
| frontend | Composable + Compose UI Test | compose-ui-test |

backend:infrastructure 層は H2 等の代用を使わず本物の PG。`uuidv7()`、`DISTINCT ON` 等 PG 固有機能を使うため。

## 10. 将来拡張(MVP 外)

- Yahoo! ショッピング / 楽天商品検索 API による catalog 自動充填(`catalog_item_external_codes` テーブル)
- 在庫減少通知(Web Push、iOS 16.4+ の PWA Push 対応)
- 購入時期予測(消費イベントの時系列から推定)
- 複数世帯間の本格運用(認可、招待、ロール管理)
- カタログ編集の競合解決ポリシー(信頼度スコア、編集権限)
- 完全オフライン対応(ローカルイベントキュー + 衝突解決)

## 11. 未決事項

1. **kotlinx-rpc の trace context 伝播**: 公式拡張ポイントの有無を実装フェーズで確認。なければ WebSocket ハンドシェイク時の HTTP ヘッダで代用。
2. **本番デプロイ先**: backend(コンテナ + 任意 PaaS)、frontend(静的ホスティング)、PostgreSQL(マネージド or 自前)— MVP 完成後に決定。
3. **PWA 用 OIDC ライブラリ**: Compose Multiplatform Wasm から使える OIDC クライアントの選定(JS 相互運用で `oidc-client-ts` を呼ぶか、KMP 対応ライブラリを使うか)— 実装フェーズで決定。

## 12. 用語集

| 用語 | 意味 |
|---|---|
| 事実テーブル | append-only な物理テーブル。1 行が 1 つのドメイン上の事実(イベント)を表現する |
| CatalogItem | 全世帯共通の商品概念(例: 「キレイキレイ 泡ハンドソープ」) |
| Product | 世帯固有の商品インスタンス。CatalogItem を「採用」したもの。世帯ごとに閾値を持つ |
| Stock | Product に対する補充/消費の事実集合 |
| 訂正 | 既存の事実を取り消さずに、新しい事実として「訂正があった」ことを記録する操作 |
