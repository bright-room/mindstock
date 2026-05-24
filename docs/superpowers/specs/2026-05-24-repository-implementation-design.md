# Repository 実装層設計 (Plan 5)

- 対象: Plan 5(Repository ポートの Exposed 実装 + Testcontainers 結合テスト + DI 配線完了 + ExposedTransactionPlugin 本実装)
- 親仕様: [2026-05-23-mindstock-design.md](./2026-05-23-mindstock-design.md)、[2026-05-23-domain-layer-design.md](./2026-05-23-domain-layer-design.md)、[2026-05-24-usecase-design.md](./2026-05-24-usecase-design.md)
- 前提: Plan 3 / domain-richness / stock-movements-unification / Plan 4 (UseCase 層) が完了済み
- 後続: Plan 6(kotlinx-rpc サービス実装、エンドポイント配線)

## 1. ゴール

`domain/repository/` の全 10 Repository ポート(5 集約 × Reader / Register)に対する Exposed 実装を追加し、Testcontainers 上の実 PostgreSQL 18 で結合テストを通す。DI 配線を完了させ、ExposedTransactionPlugin を本実装+install まで行う。Plan 5 完了時点で、エンドポイントは未だ無いがアプリ起動 → Repository テストが緑、というところまで到達する。

## 2. Subproject 構成(暫定)

Repository 実装は `:backend:application:api` 配下に置く。専用 subproject(例: `:backend:infrastructure:datasource`)は作らない。

**理由:** アプリ全体の subproject 構造に違和感があり、Plan 5 完了後に俯瞰して見直す予定(memory [[structure-review-pending]])。Plan 5 はまず動くものを揃える方針で、暫定的にアプリ層と同居させる。Repository 実装は **infrastructure 詳細** であって本来 application モジュールには属さないが、今回はレイヤー違反を許容する。将来的に分離する前提。

パッケージ階層:

```
backend/application/api/src/main/kotlin/net/brightroom/mindstock/
├── application/usecase/...          # Plan 4 の Handler 群
├── infrastructure/
│   └── datasource/
│       └── repository/
│           ├── user/
│           │   ├── UserRepositoryImpl.kt
│           │   ├── UserRegisterRepositoryImpl.kt
│           │   └── UserHydration.kt
│           ├── household/
│           │   ├── HouseholdRepositoryImpl.kt
│           │   ├── HouseholdRegisterRepositoryImpl.kt
│           │   └── HouseholdHydration.kt
│           ├── catalog/
│           │   ├── CatalogItemRepositoryImpl.kt
│           │   ├── CatalogItemRegisterRepositoryImpl.kt
│           │   └── CatalogItemHydration.kt
│           ├── product/
│           │   ├── ProductRepositoryImpl.kt
│           │   ├── ProductRegisterRepositoryImpl.kt
│           │   └── ProductHydration.kt
│           └── stock/
│               ├── StockRepositoryImpl.kt
│               ├── StockRegisterRepositoryImpl.kt
│               └── StockHydration.kt
└── configuration/
    ├── di/
    │   ├── RepositoryConfiguration.kt   # Plan 5 で新規
    │   └── UseCaseConfiguration.kt      # Plan 5 で新規
    └── transaction/
        └── ExposedTransactionPlugin.kt  # Plan 4 で skeleton、Plan 5 で本実装
```

## 3. Repository 実装の方針

### 3.1 ファイル粒度

**1 Repository ポート = 1 実装ファイル**。Reader と Register の責務分離をそのまま実装側でも保つ。例:

- `UserRepository` → `UserRepositoryImpl.kt`(1 クラス)
- `UserRegisterRepository` → `UserRegisterRepositoryImpl.kt`(1 クラス)

### 3.2 命名規約

- 実装クラスは `<ポート名>Impl`(例: `UserRepositoryImpl implements UserRepository`)
- コンストラクタ引数は `database: Database`(Exposed v1 の JDBC `Database`)
- `transaction {}` ブロックは **書かない**(後述 §6 参照)
- メソッド本体は SELECT/INSERT を書いて結果を `<Aggregate>Hydration.kt` の extension で domain object に組み立てる

### 3.3 Hydration ロジックの置き場所

各集約に対応する `<Aggregate>Hydration.kt` を作成し、internal extension を集約する。例:

```kotlin
// infrastructure/datasource/repository/user/UserHydration.kt
package net.brightroom.mindstock.infrastructure.datasource.repository.user

import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.infrastructure.datasource.schemas.user.UsersTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.user.UserDisplayNamesTable
import org.jetbrains.exposed.v1.core.ResultRow

/** `users JOIN user_display_names`(最新を DISTINCT ON で絞った行)から User を組み立てる。 */
internal fun ResultRow.toUser(): User =
    User(
        id = UserId(this[UsersTable.id].value),
        authIdentity = AuthIdentity(AuthSubject(this[UsersTable.zitadel_sub])),
        displayName = DisplayName(this[UserDisplayNamesTable.display_name]),
    )
```

複数 Repository で再利用される場合は `internal` で、ファイル外公開はしない。

### 3.4 domain object に直接マップする方針

**原則: ResultRow から直接 domain object を組み立てる**(中間 Entity を作らない)。

ただし、以下のいずれかに該当する場合は infrastructure 側に **Entity クラス**を挟むことを許可する:

- Exposed のクエリ結果が複数 ResultRow / 異なる Table の組み合わせで、1 度 Entity に詰めないと組み立てロジックが複雑になる
- 同じ生データを複数の domain object に変換する必要があり、ResultRow からの直接変換を 2 度書くことになる

Entity クラスを置く場合は `infrastructure/datasource/repository/<aggregate>/entity/` 配下に `internal data class` で配置。**外には漏らさない**(domain object に変換してから返す)。

Plan 5 で実際に Entity が必要になるかは実装時に判断。基本は不要を期待。

### 3.5 PostgreSQL `DISTINCT ON` の使用

履歴テーブルから最新行を取得する場面で `DISTINCT ON` を使う(spec §5.1 親仕様で「PG 固有機能を使う」と明言済み)。

Exposed v1 では型安全な DSL がないため、`org.jetbrains.exposed.v1.core.CustomFunction` か `Transaction.exec("SELECT ...")` で生 SQL を書く。可読性優先で生 SQL 寄りで書いて良い。例:

```kotlin
override fun findByAuthIdentity(identity: AuthIdentity): User? =
    UsersTable
        .join(
            UserDisplayNamesTable,
            JoinType.INNER,
            additionalConstraint = {
                // `DISTINCT ON (user_id) ... ORDER BY user_id, id DESC` のサブクエリと結合
            },
        )
        .select(...)
        .where { UsersTable.zitadel_sub eq identity.subject.value }
        .singleOrNull()
        ?.toUser()
```

実装時に「型安全 DSL で書ける」と判断した場合はそちらで構わない。最新行取得が**正しく動く**ことが最優先。

## 4. 集約別の hydration 仕様

各集約の hydration が DB のどのテーブル群から組み立てるかを明示する。実装時のクエリ書き方の手引き。

### 4.1 User

- テーブル: `users` + 最新 `user_display_names`
- クエリ: `users` を JOIN し、`user_display_names` から `DISTINCT ON (user_id) * ORDER BY user_id, id DESC` で最新を取る
- `findByAuthIdentity(identity)`: `WHERE zitadel_sub = ?` で絞る

### 4.2 Household

- テーブル: `households` + active な `household_memberships`(`household_membership_revocations` に対応行が無いもの)
- 各 membership の `user_id` から User を組み立てる(`UserHydration.toUser` を再利用)
- 「active」判定: `household_memberships` の `id` が `household_membership_revocations.membership_id` に存在しない
- `findOf(user)`: 「`user` が active member である Household を返す。MVP 1 ユーザー 1 世帯前提なので `singleOrNull()`」

### 4.3 CatalogItem

- テーブル: `catalog_items` + 最新 `catalog_item_revisions`
- 最新リビジョンの `name` / `unit` を CatalogItem の現在値に
- `search(query, limit)`: `WHERE name ILIKE '%query%' ORDER BY name LIMIT ?`(MVP のシンプル LIKE 検索)
- `findById(id)`: `WHERE catalog_items.id = ?`

### 4.4 Product

- テーブル: `products` + 最新 `product_minimum_stocks`(nullable)+ `product_archives` 存在チェック
- `catalogItem` は CatalogItemHydration を再利用して組み立てる
- `minimumStock`: 該当する `product_minimum_stocks` 行が存在すれば `MinimumStock(value)`、無ければ `null`
- `archived`: `EXISTS (SELECT 1 FROM product_archives WHERE product_id = ?)` を boolean に
- `listOf(household)`: `WHERE household_id = ?`(archived 含む。フィルタは domain 側 `Products.activeOnly()` 等で行う)
- `find(household, catalogItem)`: `WHERE household_id = ? AND catalog_item_id = ?`

### 4.5 Stock

- テーブル: Product(上記)+ `stock_movements`
- `stockOf(product)`: `SELECT * FROM stock_movements WHERE product_id = ? ORDER BY occurred_at, id`(全件取得して `StockMovements` に詰める)
- `stocksOf(household)`: **2 クエリ**で取る:
  1. `ProductRepository.listOf(household)` で Product 一覧
  2. `SELECT * FROM stock_movements WHERE product_id IN (?, ?, ...) ORDER BY ...` で全 movement を一括取得
  3. メモリ上で Product ID ごとにグルーピングして `Stock` を組み立てる
- `movementHistory(product, limit)`: `SELECT * FROM stock_movements WHERE product_id = ? ORDER BY occurred_at DESC, id DESC LIMIT ?`

### 4.6 Register 系の INSERT

- `UserRegisterRepository.register`: `users` + 初回 `user_display_names` の 2 INSERT(同一 transaction)
- `UserRegisterRepository.rename`: `user_display_names` に行を 1 INSERT(append-only)
- `HouseholdRegisterRepository.create`: `households` + 初回 `household_memberships(OWNER)` の 2 INSERT(同一 transaction)
- `HouseholdRegisterRepository.invite`: `household_memberships` に行を 1 INSERT
- `HouseholdRegisterRepository.revoke`: `household_membership_revocations` に行を 1 INSERT
- `CatalogItemRegisterRepository.register`: `catalog_items` + 初回 `catalog_item_revisions` の 2 INSERT(同一 transaction)
- `CatalogItemRegisterRepository.revise`: `catalog_item_revisions` に行を 1 INSERT
- `ProductRegisterRepository.adopt`: `products` に行を 1 INSERT(`product_minimum_stocks` は別途 `setMinimumStock` で)
- `ProductRegisterRepository.setMinimumStock`: `product_minimum_stocks` に行を 1 INSERT
- `ProductRegisterRepository.archive`: `product_archives` に行を 1 INSERT
- `StockRegisterRepository.replenish` / `consume`: `stock_movements` に行を 1 INSERT(type を REPLENISHMENT / CONSUMPTION で切り替え)

INSERT 後に **新規追加した行を SELECT で取り戻して domain object に組み立てて返す**(`INSERT ... RETURNING *` 相当)。Exposed の `insertAndGetId` で id を取り、`hydration` で domain object に組み立てるパターン。

## 5. テスト戦略

### 5.1 テスト分離(T1-a)

**テストメソッドごとに fresh schema** を張る。`TestContainersPostgres.withFreshSchema { jdbcUrl, schema -> ... }` を `@BeforeEach` で呼ぶか、テスト関数ごとにブロックで囲む。

```kotlin
class UserRepositoryImplIntegrationTest : FunSpec({
    test("findByAuthIdentity returns user with latest display name") {
        TestContainersPostgres.withFreshSchema { jdbcUrl, _ ->
            val ds = testHikariDataSource(jdbcUrl, TestContainersPostgres.username, TestContainersPostgres.password)
            MigrationRunner.migrate(ds)
            val db = Database.connect(ds)

            transaction(db) {
                // given: insert users + user_display_names (initial + renamed)
                // when: findByAuthIdentity
                // then: returns user with LATEST display name
            }
        }
    }
})
```

migration overhead は 1 テストあたり ~200ms 程度を想定。並列実行可。

### 5.2 テスト基盤の再利用(T2-a)

`:backend:application:api` の `build.gradle.kts` に追加:

```kotlin
testImplementation(testFixtures(projects.backend.infrastructure.migration.executor))
testImplementation(libs.testcontainers.postgresql)
testImplementation(libs.kotest.runner.junit5)
testImplementation(libs.kotest.assertions.core)
```

`TestContainersPostgres` / `testHikariDataSource` を再利用する。

### 5.3 Repository テスト専用 testFixtures(T2-a 追加分)

`:backend:application:api` 配下に `src/testFixtures/kotlin/` を作り、Repository テスト共通のヘルパーを置く:

```
backend/application/api/src/testFixtures/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/
├── RepositoryTestSupport.kt    # fresh schema + Database セットアップを束ねたヘルパー
├── DataBuilders.kt             # givenUser / givenHousehold / givenCatalogItem 等
└── ...
```

例:

```kotlin
fun withRepositoryTestContext(block: RepositoryTestContext.() -> Unit) {
    TestContainersPostgres.withFreshSchema { jdbcUrl, _ ->
        val ds = testHikariDataSource(jdbcUrl, TestContainersPostgres.username, TestContainersPostgres.password)
        MigrationRunner.migrate(ds)
        val db = Database.connect(ds)
        RepositoryTestContext(db).block()
    }
}

class RepositoryTestContext(val database: Database) {
    fun givenUser(name: String = "Alice", subject: String = "test-sub-${UUID.randomUUID()}"): User =
        UserRegisterRepositoryImpl(database).register(AuthIdentity(AuthSubject(subject)), DisplayName(name))
}
```

`:backend:application:api` の `build.gradle.kts` で `java-test-fixtures` plugin を有効化する。

### 5.4 テスト観点(T3-a/b/c)

各 Repository テストで以下の 3 観点を埋める:

- **CRUD ラウンドトリップ**: register → 同じ条件で find して同じ domain object が返る
- **履歴の最新が選ばれる**: rename / revise / setMinimumStock 等を 2 回実行 → find で**最新値**が返る
- **revoke / archive の除外**: 
  - Household: revoke 後の member は `members` に含まれない
  - Product: archive 後の Product でも `listOf(household)` には返るが `archived = true`(domain 側のフィルタで除外する想定。Repository 自身は archived 状態を正しく返せばよい)

**Plan 5 では書かない**:
- append-only 違反テスト(AppendOnlyEnforcementTest にすでにあるため重複)
- RPC 統合テスト(Plan 6 で kotlinx-rpc 配線時)

### 5.5 テストファイル構成

各 Repository 実装に対応するテストファイル(1:1)を `src/test/kotlin/` に置く:

```
backend/application/api/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/
├── user/
│   ├── UserRepositoryImplIntegrationTest.kt
│   └── UserRegisterRepositoryImplIntegrationTest.kt
├── household/
│   ├── ...
...
```

Kotest + 上記 `withRepositoryTestContext` を使う。MockK は使わない(実 DB なので)。

## 6. トランザクション境界

### 6.1 production 側

`ExposedTransactionPlugin` を **本実装 + install** する(Plan 4 spec §4 の方針に従う、Plan 5 で完成させる)。

実装は `intercept(ApplicationCallPipeline.Call) { ... newSuspendedTransaction(db = database) { proceed() } ... }` を `createApplicationPlugin` の body 内で行う。`onCall` フックでは call の全体を transaction で囲めないため。

```kotlin
val ExposedTransactionPlugin =
    createApplicationPlugin(
        name = "ExposedTransaction",
        createConfiguration = ::ExposedTransactionConfig,
    ) {
        val database =
            pluginConfig.database
                ?: error("ExposedTransactionPlugin requires `database` to be set in configuration")

        application.intercept(ApplicationCallPipeline.Call) {
            newSuspendedTransaction(db = database) {
                proceed()
            }
        }
    }
```

`application.yaml` の `ktor.application.modules` に新たな `transactionConfigure` module を追加して install する:

```yaml
modules:
  - "net.brightroom.mindstock.configuration.di.DependenciesConfigurationKt.dependenciesConfigure"
  - "net.brightroom.mindstock.configuration.migration.MigrationConfigurationKt.migrationConfigure"
  - "net.brightroom.mindstock.configuration.external.exposed.ExposedConfigurationKt.exposedConfigure"
  - "net.brightroom.mindstock.configuration.transaction.TransactionConfigurationKt.transactionConfigure"  # 追加
  - "net.brightroom.mindstock.configuration.di.RepositoryConfigurationKt.repositoryConfigure"             # 追加
  - "net.brightroom.mindstock.configuration.di.UseCaseConfigurationKt.usecaseConfigure"                   # 追加
  - "net.brightroom.mindstock.configuration.logging.LoggingConfigurationKt.loggingConfigure"
  - "net.brightroom.mindstock.configuration.routing.RoutingConfigurationKt.routingConfigure"
```

```kotlin
// configuration/transaction/TransactionConfiguration.kt
fun Application.transactionConfigure() {
    val database: Database by dependencies  // Ktor DI から解決
    install(ExposedTransactionPlugin) {
        this.database = database
    }
}
```

Plan 5 段階では RPC エンドポイントが存在しないので install しても実質呼ばれる Handler は無いが、将来 RPC が増えれば自動で適用される。

### 6.2 test 側

`newSuspendedTransaction` ではなく **同期** `transaction(db) { ... }` を使う。Kotest は同期テストフレームワークで、suspend を強要しないため。テスト内で Repository を呼ぶときは:

```kotlin
transaction(db) {
    val user = UserRegisterRepositoryImpl(db).register(identity, name)
    val found = UserRepositoryImpl(db).findByAuthIdentity(identity)
    found shouldBe user
}
```

Repository 実装内では `transaction {}` を書かないため、テスト側で囲まないとクエリが動かない。

## 7. DI 配線

Plan 4 spec §7 で「Plan 5 で実施」と decision した DI 登録を行う。

### 7.1 `RepositoryConfiguration.kt`

```kotlin
// configuration/di/RepositoryConfiguration.kt
fun Application.repositoryConfigure() {
    dependencies {
        provide<UserRepository> { UserRepositoryImpl(resolve()) }
        provide<UserRegisterRepository> { UserRegisterRepositoryImpl(resolve()) }
        provide<HouseholdRepository> { HouseholdRepositoryImpl(resolve()) }
        provide<HouseholdRegisterRepository> { HouseholdRegisterRepositoryImpl(resolve()) }
        provide<CatalogItemRepository> { CatalogItemRepositoryImpl(resolve()) }
        provide<CatalogItemRegisterRepository> { CatalogItemRegisterRepositoryImpl(resolve()) }
        provide<ProductRepository> { ProductRepositoryImpl(resolve()) }
        provide<ProductRegisterRepository> { ProductRegisterRepositoryImpl(resolve()) }
        provide<StockRepository> { StockRepositoryImpl(resolve()) }
        provide<StockRegisterRepository> { StockRegisterRepositoryImpl(resolve()) }
    }
}
```

`resolve()` は Ktor DI が `Database` を解決する。

### 7.2 `UseCaseConfiguration.kt`

```kotlin
// configuration/di/UseCaseConfiguration.kt
fun Application.usecaseConfigure() {
    dependencies {
        provide<RegisterUserHandler> { RegisterUserHandler(resolve()) }
        provide<RenameUserHandler> { RenameUserHandler(resolve()) }
        // ... 全 20 Handler
    }
}
```

### 7.3 順序

`application.yaml` の modules 列挙順は、依存方向と一致させる:

1. `dependenciesConfigure`(既存、空)
2. `migrationConfigure`(既存)
3. `exposedConfigure`(既存、`Database` を provide)
4. **`transactionConfigure`(新規、`ExposedTransactionPlugin` install)**
5. **`repositoryConfigure`(新規、Repository 実装を provide)**
6. **`usecaseConfigure`(新規、Handler を provide)**
7. `loggingConfigure`(既存)
8. `routingConfigure`(既存)

## 8. 親仕様の更新

本仕様の決定に合わせて、親仕様の以下を更新する(Plan 5 の Task 0 で実施):

- `usecase-design.md` §7: 「Plan 5 で実施」を「Plan 5 で完了済み(`RepositoryConfiguration.kt` / `UseCaseConfiguration.kt`)」に
- `usecase-design.md` §10: 「kotlinx-rpc サービス IF と Handler 配線(Plan 6)」は維持。「Repository の Exposed 実装(Plan 5)」を「完了」に変更
- `mindstock-design.md` の dependency graph: Repository 実装サブプロジェクトを明示するか、または「現状は `:backend:application:api` に同居(将来 `:backend:infrastructure:datasource` 等に分離予定)」と注記を追加

## 9. 対象外(明示)

本仕様で **やらないこと**(Plan 6 / 別途で扱う):

- kotlinx-rpc サービス IF 実装(Plan 6)
- RPC エンドポイントと Handler の配線(Plan 6)
- DomainException → RPC error マッピング(Plan 6)
- 認証(JWT 検証 / current user 取得)実装
- backend subproject 構造の見直し(Plan 5 完了後、別作業として実施)
- Stock movement の snapshot 化(将来の性能対策)
- append-only 違反防止の追加テスト(`AppendOnlyEnforcementTest` で既にカバー)
