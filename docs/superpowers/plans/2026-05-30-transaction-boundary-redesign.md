# トランザクション境界 再設計 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** presentation 層の `tx()` を解体し、トランザクション境界を各 DataSource メソッド内へ下ろす。presentation は `rpcBoundary`(guard + 例外→RpcError 変換 + log)のみを担う。

**Architecture:** DataSource の各メソッドを `suspend` 化し内部で `newSuspendedTransaction(db)` を張る(`suspend` は Repository interface → Service へ伝播)。Controller は `Database` 依存を失い、各メソッドを `rpcBoundary(session) { ... }`(ドメイン値を返すと `RpcResult.Ok` で包む)で囲む。`UserPublicController.register` の再呼び出しは `RequireUnregisteredUserPlugin` で routing 層で遮断する。

**Tech Stack:** Kotlin Multiplatform / Ktor / kotlinx-rpc 0.10.2 / Exposed 1.3.0(JDBC, `newSuspendedTransaction`)/ kotlinx-coroutines / Kotest(FunSpec)/ MockK / PostgreSQL(append-only role)

**設計の出典:** [docs/superpowers/specs/2026-05-30-transaction-boundary-redesign-design.md](../specs/2026-05-30-transaction-boundary-redesign-design.md)

---

## 重要な前提と落とし穴(実装前に必ず読む)

1. **Exposed の DSL は transaction 内でのみ動作する。** `newSuspendedTransaction(db = database) { ... }` で囲まないと "No transaction in context" で落ちる。
2. **ネストトランザクションが発生する箇所がある。** `StockDataSource` は `ProductRepository` をコンストラクタ注入しており、`stocksOf` の中で `productRepository.listOf(household)` を呼ぶ。両方が `newSuspendedTransaction` を張ると入れ子になる。これは現状の `DatabaseConfig { useNestedTransactions = true }`(`ExposedConfiguration.kt`)が SAVEPOINT で吸収するので**動作する**。この設定は**残す**。
3. **`suspend` 伝播の連鎖**: DataSource を `suspend` 化 → Repository interface も `suspend` → Service も `suspend` → Controller は元から `suspend`(変更不要)。`StockDataSource` のように Repository を内部で呼ぶ DataSource は、その呼び出しも `suspend` になるため `stocksOf` 等も `suspend` 化する。
4. **`Database` の取得経路**: 現状 DataSource は DI で `provide<XxxRepository> { XxxDataSource() }`(引数なし)。本改修で各 DataSource は `Database` をコンストラクタで受け取る(`provide<XxxRepository> { XxxDataSource(resolve()) }`)。`StockDataSource` は既に `resolve()` を 1 つ取るので `Database` が増えて 2 引数になる。
5. **append-only role**: 実行時ロールは INSERT/SELECT のみ。`insertIgnore` 等は使わない(本計画では新規導入しない)。
6. **テスト DB**: 統合テストは `@Tags("integration")`。ローカルでは `docker compose up -d postgres` 前提。実行は `./gradlew integrationTest`。単体は `./gradlew test`。
7. **コミット粒度**: 各 Task 末尾でコミット。ビルドが通らない中間状態を避けるため、Task は「core を suspend 化 → api を追従」の順で、各 Task 完了時に**当該モジュールがコンパイルできる**ようにする。ただし core を suspend 化した直後は api がまだ古い `tx()` 経由で呼ぶためコンパイルエラーになる。これを避けるため **Task 1〜4(core)と Task 5〜10(api)は連続した 1 つの作業群**とし、全体完了まで `./gradlew build` は通らない。各 Task のコミットは「論理的なまとまり」を表す(CI のグリーンは最終 Task で担保)。

> **補足:** TDD を厳密に適用するのは新規ロジック(`rpcBoundary` / `RequireUnregisteredUserPlugin` / register 遮断の E2E)に対して。既存メソッドの機械的な `suspend` 付与は「シグネチャ変更 + 既存テストの suspend 追従」で扱い、テストの追加より既存テストの維持で正しさを担保する。

---

## ファイル構成(変更マップ)

**`:backend:core`(suspend 化)**
- Modify: `application/repository/**/*Repository.kt`(10 interface)— 全メソッドに `suspend`
- Modify: `infrastructure/datasource/**/*DataSource.kt`(10 実装)— `suspend` + `newSuspendedTransaction(db)`、`Database` をコンストラクタ注入
- Modify: `application/service/**/*Service.kt`(10 実装)— 全メソッドに `suspend`
- Modify: core のテスト(`runTest` で suspend 呼び出しに追従)

**`:backend:api`(境界の再配置)**
- Create: `configuration/rpc/RpcBoundary.kt`(`rpcBoundary` — `tx()` の後継)
- Delete: `configuration/transaction/Transaction.kt`(`tx()`)
- Create: `configuration/auth/RequireUnregisteredUserPlugin.kt`
- Modify: `presentation/rpc/**/*Controller.kt`(6)— `Database` 除去、`rpcBoundary` 化
- Modify: `configuration/di/DependenciesConfiguration.kt`— DataSource に `resolve<Database>()`、Controller から `db` 除去
- Modify: `configuration/routing/RoutingConfiguration.kt`— `/user/public` に guard install
- Modify: `configuration/auth/MindstockAuthPlugin.kt`— 現状維持(認証 tx は対象外、コメント追記のみ)
- Modify/Delete: `test/configuration/transaction/TxWithGuardTest.kt` → `rpcBoundary` のテストへ
- Modify: `testFixtures/.../RepositoryTestSupport.kt`— `tx { }` ヘルパーの扱い見直し
- Modify: 各 Controller テスト / E2E の期待値更新

**rule ドキュメント**
- Modify: `.claude/rules/rpc-and-transactions.md`
- Modify: `.claude/rules/software-architecture.md`

---

## Task 1: Repository interface を `suspend` 化

**Files:**
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/user/UserRepository.kt`
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/user/UserRegisterRepository.kt`
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/household/HouseholdRepository.kt`
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/household/HouseholdRegisterRepository.kt`
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/catalog/CatalogItemRepository.kt`
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/catalog/CatalogItemRegisterRepository.kt`
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/product/ProductRepository.kt`
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/product/ProductRegisterRepository.kt`
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/stock/StockRepository.kt`
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/stock/StockRegisterRepository.kt`

- [ ] **Step 1: 全 Repository interface のメソッドに `suspend` を付ける**

各ファイルの `fun ` を `suspend fun ` に置換する(interface 宣言なので本体なし)。例(`UserRegisterRepository.kt`):

```kotlin
package net.brightroom.mindstock.application.repository.user

import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile

interface UserRegisterRepository {
    /** users + 初回 user_display_names を 1 トランザクションで INSERT。 */
    suspend fun register(
        identity: AuthIdentity,
        defaultDisplayName: DisplayName,
    ): Profile

    /** user_display_names に新規行を INSERT。 */
    suspend fun rename(
        userId: UserId,
        newName: DisplayName,
    )
}
```

`UserRepository.kt` の例:

```kotlin
interface UserRepository {
    suspend fun findProfileByAuthIdentity(identity: AuthIdentity): Profile
    suspend fun findProfileById(id: UserId): Profile
}
```

残り 8 interface も同様に、宣言された全 `fun` を `suspend fun` にする。KDoc・引数・戻り値は変更しない。

- [ ] **Step 2: core がまだコンパイルできないことを確認(DataSource 実装が未追従)**

Run: `./gradlew :backend:core:compileKotlin -q`
Expected: FAIL。`XxxDataSource` が interface を override していない旨のエラー(`'register' overrides nothing` 等)。これは Task 2 で解消する。**この Task では失敗が正しい。**

- [ ] **Step 3: コミット**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository
git commit -m "refactor(core): Repository interface の全メソッドを suspend 化

トランザクション境界を DataSource 内に下ろす準備。実装の追従は後続コミット。"
```

---

## Task 2: DataSource を `suspend` + `newSuspendedTransaction` 化

**Files:**
- Modify: 全 10 DataSource
  - `infrastructure/datasource/user/UserDataSource.kt`
  - `infrastructure/datasource/user/UserRegisterDataSource.kt`
  - `infrastructure/datasource/household/HouseholdDataSource.kt`
  - `infrastructure/datasource/household/HouseholdRegisterDataSource.kt`
  - `infrastructure/datasource/catalog/CatalogItemDataSource.kt`
  - `infrastructure/datasource/catalog/CatalogItemRegisterDataSource.kt`
  - `infrastructure/datasource/product/ProductDataSource.kt`
  - `infrastructure/datasource/product/ProductRegisterDataSource.kt`
  - `infrastructure/datasource/stock/StockDataSource.kt`
  - `infrastructure/datasource/stock/StockRegisterDataSource.kt`

**方針:** 各 DataSource は `Database` をコンストラクタで受け取り、override する各メソッドを `suspend fun` にして本体全体を `newSuspendedTransaction(db = database) { ... }` で囲む。

- [ ] **Step 1: `UserRegisterDataSource` を変換**

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.user

import net.brightroom.mindstock.application.repository.user.UserRegisterRepository
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class UserRegisterDataSource(
    private val database: Database,
) : UserRegisterRepository {
    override suspend fun register(
        identity: AuthIdentity,
        defaultDisplayName: DisplayName,
    ): Profile =
        newSuspendedTransaction(db = database) {
            val insertedUserId =
                UsersTable.insert {
                    it[zitadel_sub] = identity.subject()
                } get UsersTable.id

            UserDisplayNamesTable.insert {
                it[user_id] = insertedUserId
                it[display_name] = defaultDisplayName()
            }

            (UsersTable innerJoin UserDisplayNamesTable)
                .selectAll()
                .where { UsersTable.id eq insertedUserId }
                .single()
                .toProfile()
        }

    override suspend fun rename(
        userId: UserId,
        newName: DisplayName,
    ) {
        newSuspendedTransaction(db = database) {
            UserDisplayNamesTable.insert {
                it[user_id] = userId()
                it[display_name] = newName()
            }
        }
    }
}
```

- [ ] **Step 2: `UserDataSource`(read)を変換**

`queryLatest` は private helper。`newSuspendedTransaction` の中で Exposed クエリが動く必要があるため、各 public メソッドを `suspend` 化し本体を tx で囲む。`queryLatest` 自体は tx 内から呼ばれる非 suspend のままでよい(Exposed クエリは tx コンテキストで実行される)。

```kotlin
@OptIn(ExperimentalUuidApi::class)
class UserDataSource(
    private val database: Database,
) : UserRepository {
    override suspend fun findProfileByAuthIdentity(identity: AuthIdentity): Profile =
        newSuspendedTransaction(db = database) {
            queryLatest { UsersTable.zitadel_sub eq identity.subject() }
                ?: throw ResourceNotFoundException("user not found")
        }

    override suspend fun findProfileById(id: UserId): Profile =
        newSuspendedTransaction(db = database) {
            queryLatest { UsersTable.id eq id() }
                ?: throw ResourceNotFoundException("user not found")
        }

    private fun queryLatest(where: () -> Op<Boolean>): Profile? {
        val latest = latestDisplayNames()
        return UsersTable
            .join(latest.alias, JoinType.INNER, onColumn = UsersTable.id, otherColumn = latest.userId)
            .join(UserDisplayNamesTable, JoinType.INNER) {
                (UserDisplayNamesTable.user_id eq latest.userId) and
                    (UserDisplayNamesTable.id eq latest.maxId)
            }.selectAll()
            .where { where() }
            .singleOrNull()
            ?.toProfile()
    }
}
```

import に `org.jetbrains.exposed.v1.jdbc.Database` と `...transactions.experimental.newSuspendedTransaction` を追加。

- [ ] **Step 3: 残り 7 DataSource を同じパターンで変換**

各ファイルで:
1. コンストラクタに `private val database: Database` を追加(`StockDataSource` は既存の `productRepository` に**加えて** `database` を持つ → 2 引数)
2. override する各メソッドを `suspend fun` 化
3. メソッド本体全体を `newSuspendedTransaction(db = database) { ... }` で囲む
4. import 追加: `org.jetbrains.exposed.v1.jdbc.Database`, `org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction`

`StockDataSource` の注意点 — `stockOf` / `stocksOf` / `movementHistory` を `suspend` 化。`stocksOf` 内の `productRepository.listOf(household)` は `suspend` 呼び出しになる(`ProductRepository.listOf` が Task 1 で suspend 化済み)。`loadMovementsFor` は private helper のままでよいが、`stocksOf` から `productRepository.listOf` を呼ぶ箇所が tx の中に入ることでネスト tx になる(`useNestedTransactions = true` で吸収)。`StockDataSource` コンストラクタ:

```kotlin
class StockDataSource(
    private val productRepository: ProductRepository,
    private val database: Database,
) : StockRepository {
    override suspend fun stockOf(product: Product): Stock =
        newSuspendedTransaction(db = database) {
            val movements = loadMovementsFor(listOf(product))[product.id()] ?: emptyList()
            Stock(product, StockMovements(movements))
        }

    override suspend fun stocksOf(household: Household): Stocks =
        newSuspendedTransaction(db = database) {
            val products = productRepository.listOf(household).list   // ネスト tx(SAVEPOINT)
            if (products.isEmpty()) {
                Stocks(emptyList())
            } else {
                val byProductId = loadMovementsFor(products)
                Stocks(products.map { p -> Stock(p, StockMovements(byProductId[p.id()] ?: emptyList())) })
            }
        }

    override suspend fun movementHistory(product: Product, limit: Int): StockMovements =
        newSuspendedTransaction(db = database) {
            require(limit > 0) { "limit must be > 0" }
            // ... 既存の rows 構築をそのまま tx 内へ ...
            StockMovements(rows)
        }

    private fun loadMovementsFor(products: List<Product>): Map<Uuid, List<StockMovement>> {
        // 既存のまま(tx 内から呼ばれる)
    }
}
```

> **注意:** `productRepository.listOf` が内部で別 tx を張るため、`stocksOf` は「外側 tx + 内側 tx(SAVEPOINT)」になる。read のみなので整合性問題はない。もしネストを避けたい場合の代替は「`StockDataSource` が `ProductRepository` ではなく直接 `ProductsTable` を引く」だが、本計画では現状の委譲を維持しネストを許容する(`useNestedTransactions = true` 前提)。

- [ ] **Step 4: core のコンパイルを確認**

Run: `./gradlew :backend:core:compileKotlin -q`
Expected: PASS(Service はまだ非 suspend だが、Service → Repository 呼び出しは「suspend を非 suspend から呼ぶ」エラーになる)。

実際には **FAIL** する: Service が非 suspend のまま `suspend` Repository を呼ぶため「Suspend function 'register' should be called only from a coroutine or another suspend function」。これは Task 3 で解消。**この Step では Service 由来のエラーのみが残っていることを確認**(DataSource の override エラーが消えていればよい)。

- [ ] **Step 5: コミット**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource
git commit -m "refactor(core): DataSource を suspend + newSuspendedTransaction 化

各 DataSource メソッドが自分の transaction 境界を張る。Database をコンストラクタ注入。
StockDataSource は productRepository に加え database を受け取り、stocksOf は
ネスト tx(useNestedTransactions=true で吸収)になる。"
```

---

## Task 3: Service を `suspend` 化

**Files:**
- Modify: 全 10 Service
  - `application/service/user/UserService.kt`
  - `application/service/user/UserRegisterService.kt`
  - `application/service/household/HouseholdService.kt`
  - `application/service/household/HouseholdRegisterService.kt`
  - `application/service/catalog/CatalogItemService.kt`
  - `application/service/catalog/CatalogItemRegisterService.kt`
  - `application/service/product/ProductService.kt`
  - `application/service/product/ProductRegisterService.kt`
  - `application/service/stock/StockService.kt`
  - `application/service/stock/StockRegisterService.kt`

- [ ] **Step 1: 全 Service の public メソッドに `suspend` を付ける**

各 Service の `fun ` を `suspend fun ` に置換。本体(Repository への素通し)は変更不要。例(`UserRegisterService.kt`):

```kotlin
class UserRegisterService(
    private val userRegisterRepository: UserRegisterRepository,
) {
    suspend fun register(
        identity: AuthIdentity,
        defaultDisplayName: DisplayName,
    ): Profile = userRegisterRepository.register(identity, defaultDisplayName)

    suspend fun rename(
        userId: UserId,
        newName: DisplayName,
    ) {
        userRegisterRepository.rename(userId, newName)
    }
}
```

`StockService.kt` の例(複数 Repository 呼び出しも素通し):

```kotlin
class StockService(
    private val stockRepository: StockRepository,
) {
    suspend fun get(product: Product): Stock = stockRepository.stockOf(product)
    suspend fun list(household: Household): Stocks = stockRepository.stocksOf(household)
    suspend fun getMovementHistory(product: Product, limit: Int): StockMovements =
        stockRepository.movementHistory(product, limit)
}
```

残り 8 Service も同様。

- [ ] **Step 2: core のコンパイルを確認**

Run: `./gradlew :backend:core:compileKotlin -q`
Expected: PASS。core のプロダクションコードは suspend で一貫した。

- [ ] **Step 3: コミット**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/application/service
git commit -m "refactor(core): Service の全メソッドを suspend 化

Repository が suspend 化したことに追従。Service は引き続き素通し。"
```

---

## Task 4: core のテストを suspend 追従

**Files:**
- Modify: `backend/api/src/testFixtures/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/RepositoryTestSupport.kt`
- Modify: core の DataSource 統合テスト群(`backend/api/src/test/kotlin/.../infrastructure/datasource/repository/**/*IntegrationTest.kt`)

> **注意:** DataSource の統合テストは `:backend:api` の testFixtures / test 配下にある(`RepositoryTestSupport` の場所より)。`:backend:core` 自体には統合テストは少ない。実際の配置は `git grep -l "withRepositoryTestContext"` で確認すること。

- [ ] **Step 1: `RepositoryTestSupport` の `tx` ヘルパーを suspend 対応にする**

現状の `tx { }` は「テスト内で Repository 呼び出しを 1 transaction にまとめる」用途。DataSource が自分で tx を張るようになったため、テストの `tx { repo.foo() }` は **二重 tx** になる(`useNestedTransactions` で動くが意味は薄い)。

移行方針: `RepositoryTestContext` に DataSource をテスト内で組み立てるための `database` を残し、テストは `tx { }` で囲まず DataSource を直接 `runBlocking`/`runTest` で呼ぶ形にする。`tx` ヘルパーは「生 Exposed クエリでテストデータを直接 seed する」用途に限定して残す(削除はしない)。

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.repository

import net.brightroom.mindstock.test.TestDataSource
import net.brightroom.mindstock.test.testHikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun withRepositoryTestContext(block: RepositoryTestContext.() -> Unit) {
    TestDataSource.withFreshSchema { jdbcUrl, _ ->
        val dataSource =
            testHikariDataSource(jdbcUrl, TestDataSource.user, TestDataSource.password)
        try {
            Flyway
                .configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate()
            val database = Database.connect(dataSource)
            RepositoryTestContext(database).block()
        } finally {
            dataSource.close()
        }
    }
}

class RepositoryTestContext(
    val database: Database,
) {
    /** 生 Exposed クエリ(テストデータ seed 等)を transaction 境界内で実行するショートカット。 */
    fun <T> tx(block: () -> T): T = transaction(database) { block() }
}
```

(`RepositoryTestSupport` 自体の変更は最小。`database` を公開している点が重要。)

- [ ] **Step 2: 統合テストの DataSource 呼び出しを suspend 対応にする**

各統合テスト(例 `UserRegisterDataSourceIntegrationTest.kt`)で:
1. DataSource のコンストラクタに `database` を渡す(`UserRegisterDataSource(database)`)
2. DataSource メソッド呼び出しは `suspend` なので `runBlocking { ... }` または Kotest の `coroutine` で囲む。現状 `tx { repo.register(...) }` だった箇所を `runBlocking { repo.register(...) }` に置換(DataSource が内部で tx を張るため外側 `tx` は不要)

変換例(`UserRegisterDataSourceIntegrationTest.kt` の 1 ケース):

```kotlin
// before:
//   val registerRepo = UserRegisterDataSource()
//   val profile = tx { registerRepo.register(identity, DisplayName("Alice")) }
//   tx { registerRepo.rename(profile.userId, DisplayName("Alicia")) }
//   val refetched = tx { readerRepo.findProfileByAuthIdentity(identity) }

// after:
import kotlinx.coroutines.runBlocking

val registerRepo = UserRegisterDataSource(database)
val readerRepo = UserDataSource(database)
val profile = runBlocking { registerRepo.register(identity, DisplayName("Alice")) }
runBlocking { registerRepo.rename(profile.userId, DisplayName("Alicia")) }
val refetched = runBlocking { readerRepo.findProfileByAuthIdentity(identity) }
```

`StockDataSource` を使うテストは `StockDataSource(productRepository, database)` の 2 引数になる点に注意。生データ seed(`UsersTable.insert { }` を直接呼ぶ箇所)は引き続き `tx { }` で囲む(これは Exposed 生クエリなので tx 境界が必要)。

全 DataSource 統合テスト(catalog / user / household / product / stock の `*IntegrationTest.kt` と `*RegisterDataSourceIntegrationTest.kt`)に同じ変換を適用する。

- [ ] **Step 3: 統合テストを実行**

Run: `./gradlew integrationTest -q`
Expected: PASS(全 DataSource 統合テストが緑)。

> ローカルで Postgres 未起動なら `docker compose up -d postgres` を先に実行。

- [ ] **Step 4: コミット**

```bash
git add backend/api/src/testFixtures backend/api/src/test/kotlin/net/brightroom/mindstock/infrastructure
git commit -m "test(core): DataSource 統合テストを suspend 呼び出しに追従

DataSource が自分で tx を張るため、テストの外側 tx{} を runBlocking{} に置換。
生データ seed のみ tx{} を維持。DataSource は database をコンストラクタで受ける。"
```

---

## Task 5: `rpcBoundary` を新規作成(TDD)

**Files:**
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/rpc/RpcBoundary.kt`
- Create: `backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/rpc/RpcBoundaryTest.kt`

**設計:** `rpcBoundary` は transaction を張らない。`session.exp` guard → `supervisorScope { block() }` → 成功は `RpcResult.Ok(result)` で包む → 例外は `RpcError` に翻訳 → 1 行 JSON ログ。`block` はドメイン値 `T` を返す(`RpcResult` ではない)。

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.configuration.rpc

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class RpcBoundaryTest :
    FunSpec({
        fun sessionWith(exp: Instant): MindstockSession =
            MindstockSession(
                identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub")),
                userId = UserId(Uuid.random()),
                exp = exp,
                callId = Uuid.random(),
            )

        test("session.exp が過去 → Err(Unauthorized(token expired))、block は呼ばれない") {
            var called = false
            val result =
                runBlocking {
                    rpcBoundary(sessionWith(Clock.System.now() - 1.hours)) {
                        called = true
                        1
                    }
                }
            result.shouldBeInstanceOf<RpcResult.Err<RpcError>>()
            result.error.shouldBeInstanceOf<RpcError.Unauthorized>()
            (result.error as RpcError.Unauthorized).reason shouldBe "token expired"
            called shouldBe false
        }

        test("正常系: block の戻り値が RpcResult.Ok に包まれる") {
            val result =
                runBlocking {
                    rpcBoundary(sessionWith(Clock.System.now() + 1.hours)) { "hello" }
                }
            result shouldBe RpcResult.Ok("hello")
        }

        test("block 内で ResourceNotFoundException → Err(NotFound) でメッセージがパススルー") {
            val result =
                runBlocking {
                    rpcBoundary<Int>(sessionWith(Clock.System.now() + 1.hours)) {
                        throw ResourceNotFoundException("household not found: test-id")
                    }
                }
            result.shouldBeInstanceOf<RpcResult.Err<RpcError>>()
            val err = result.error
            err.shouldBeInstanceOf<RpcError.NotFound>()
            err.message shouldBe "household not found: test-id"
        }

        test("block 内で IllegalStateException → Err(Internal)") {
            val result =
                runBlocking {
                    rpcBoundary<Int>(sessionWith(Clock.System.now() + 1.hours)) { error("boom") }
                }
            result.shouldBeInstanceOf<RpcResult.Err<RpcError>>()
            result.error.shouldBeInstanceOf<RpcError.Internal>()
        }
    })
```

> このテストは `tx()` と違い `Database` を一切要らない(transaction を張らないため)。`@Tags("integration")` は不要 = 単体テストとして走る。

- [ ] **Step 2: テストが失敗(コンパイル不可)することを確認**

Run: `./gradlew :backend:api:compileTestKotlin -q`
Expected: FAIL。`rpcBoundary` 未定義(unresolved reference)。

- [ ] **Step 3: `rpcBoundary` を実装**

```kotlin
package net.brightroom.mindstock.configuration.rpc

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.supervisorScope
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import kotlin.uuid.ExperimentalUuidApi

private val logger = KotlinLogging.logger {}
private val callLogJson = Json { encodeDefaults = true }

@Serializable
private data class RpcCallLogEntry(
    val callId: String,
    val userId: String?,
    val outcome: String,
    val elapsedMs: Long,
)

/**
 * RPC message-scoped 境界。**transaction は張らない**(各 DataSource が自分で張る)。
 *
 * - session.exp が現在時刻を超えていたら即 Err(Unauthorized("token expired"))
 * - block はドメイン値 T を返す。成功時は RpcResult.Ok(result) に包む
 * - ResourceNotFoundException → Err(NotFound) / その他 Throwable → Err(Internal)
 * - CancellationException は伝播
 * - supervisorScope は kRPC server scope へのエラー leak 防止のため維持
 * - 各呼び出しごとに callId / userId / outcome / elapsedMs を 1 行 JSON でログ出力
 */
@OptIn(ExperimentalUuidApi::class)
suspend fun <T> rpcBoundary(
    session: MindstockSession,
    block: suspend () -> T,
): RpcResult<T, RpcError> {
    val start = Clock.System.now()
    if (start > session.exp) {
        emitLog(session, start, outcome = "Err:Unauthorized(expired)")
        return RpcResult.Err(RpcError.Unauthorized(reason = "token expired"))
    }
    return try {
        val result = supervisorScope { block() }
        emitLog(session, start, outcome = "Ok")
        RpcResult.Ok(result)
    } catch (e: CancellationException) {
        throw e
    } catch (e: ResourceNotFoundException) {
        emitLog(session, start, outcome = "Err:NotFound")
        RpcResult.Err(RpcError.NotFound(message = e.message.orEmpty()))
    } catch (e: Throwable) {
        logger.error(e) { "unhandled exception during RPC call_id=${session.callId}" }
        emitLog(session, start, outcome = "Throwable:${e::class.simpleName}")
        RpcResult.Err(RpcError.Internal(reason = "unexpected server error"))
    }
}

@OptIn(ExperimentalUuidApi::class)
private fun emitLog(
    session: MindstockSession,
    start: Instant,
    outcome: String,
) {
    val elapsedMs = (Clock.System.now() - start).inWholeMilliseconds
    val entry =
        RpcCallLogEntry(
            callId = session.callId.toString(),
            userId = session.userId?.toString(),
            outcome = outcome,
            elapsedMs = elapsedMs,
        )
    logger.info { "rpc call ${callLogJson.encodeToString(entry)}" }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :backend:api:test --tests "net.brightroom.mindstock.configuration.rpc.RpcBoundaryTest" -q`
Expected: PASS(4 ケース)。

> api 全体はまだコンパイルできない(Controller が `tx()` を使い続けているため)。このテストは単体クラス指定で先行検証する。もし api モジュール全体のコンパイルが先に失敗してこのテストが走らない場合は、Task 6 以降と合わせて Task 10 でまとめて検証する。

- [ ] **Step 5: コミット**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/rpc backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/rpc
git commit -m "feat(api): rpcBoundary を追加(tx を張らない presentation 境界)

guard + 例外→RpcError 変換 + log + supervisorScope のみ担う。
block はドメイン値を返し、成功時に RpcResult.Ok で包む。"
```

---

## Task 6: 全 Controller を `rpcBoundary` 化、`Database` 依存を除去

**Files:**
- Modify: `presentation/rpc/user/UserPublicController.kt`
- Modify: `presentation/rpc/user/UserController.kt`
- Modify: `presentation/rpc/household/HouseholdController.kt`
- Modify: `presentation/rpc/catalog/CatalogController.kt`
- Modify: `presentation/rpc/product/ProductController.kt`
- Modify: `presentation/rpc/stock/StockController.kt`

**方針:** 各 Controller から `private val database: Database` を削除。`import ...configuration.transaction.tx` を `import ...configuration.rpc.rpcBoundary` に置換。各メソッドの `tx(database, session) { ... RpcResult.Ok(x) }` を `rpcBoundary(session) { ... x }` に変換(`RpcResult.Ok(...)` の包みを外し、ドメイン値を直接返す)。

- [ ] **Step 1: `StockController` を変換**

```kotlin
package net.brightroom.mindstock.presentation.rpc.stock

import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.product.ProductService
import net.brightroom.mindstock.application.service.stock.StockRegisterService
import net.brightroom.mindstock.application.service.stock.StockService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.rpc.rpcBoundary
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.stock.Stock
import net.brightroom.mindstock.domain.model.stock.Stocks
import net.brightroom.mindstock.domain.model.stock.movement.Note
import net.brightroom.mindstock.domain.model.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.stock.movement.Quantity
import net.brightroom.mindstock.domain.model.stock.movement.StockMovements
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import net.brightroom.mindstock.rpc.StockRpcService

class StockController(
    private val stockService: StockService,
    private val stockRegisterService: StockRegisterService,
    private val productService: ProductService,
    private val householdService: HouseholdService,
    private val session: MindstockSession,
) : StockRpcService {
    override suspend fun get(productId: ProductId): RpcResult<Stock, RpcError> =
        rpcBoundary(session) {
            val product = productService.findById(productId)
            stockService.get(product)
        }

    override suspend fun list(householdId: HouseholdId): RpcResult<Stocks, RpcError> =
        rpcBoundary(session) {
            val household = householdService.findById(householdId)
            stockService.list(household)
        }

    override suspend fun movementHistory(
        productId: ProductId,
        limit: Int,
    ): RpcResult<StockMovements, RpcError> =
        rpcBoundary(session) {
            val product = productService.findById(productId)
            stockService.getMovementHistory(product, limit)
        }

    override suspend fun replenish(
        productId: ProductId,
        qty: Quantity,
        occurredAt: OccurredAt,
        note: Note,
    ): RpcResult<Unit, RpcError> =
        rpcBoundary(session) {
            val product = productService.findById(productId)
            stockRegisterService.replenish(product, qty, occurredAt, requireNotNull(session.userId), note)
        }

    override suspend fun consume(
        productId: ProductId,
        qty: Quantity,
        occurredAt: OccurredAt,
        note: Note,
    ): RpcResult<Unit, RpcError> =
        rpcBoundary(session) {
            val product = productService.findById(productId)
            stockRegisterService.consume(product, qty, occurredAt, requireNotNull(session.userId), note)
        }
}
```

> `replenish` / `consume` は `Unit` を返すメソッド。`rpcBoundary(session) { ...; stockRegisterService.consume(...) }` の最後の式が `Unit` なので `RpcResult<Unit, RpcError>` に正しく包まれる。

- [ ] **Step 2: 残り 5 Controller を同じパターンで変換**

各 Controller で:
1. コンストラクタから `private val database: Database,` を削除
2. `import org.jetbrains.exposed.v1.jdbc.Database` を削除
3. `import net.brightroom.mindstock.configuration.transaction.tx` → `import net.brightroom.mindstock.configuration.rpc.rpcBoundary`
4. 各メソッド `tx(database, session) { ... RpcResult.Ok(expr) }` → `rpcBoundary(session) { ... expr }`(`RpcResult.Ok` を外す)。`Unit` 返却メソッドは最後の文をそのまま(`RpcResult.Ok(Unit)` の行を削除)

`UserPublicController.kt` の変換後:

```kotlin
package net.brightroom.mindstock.presentation.rpc.user

import net.brightroom.mindstock.application.service.user.UserRegisterService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.rpc.rpcBoundary
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import net.brightroom.mindstock.rpc.UserPublicRpcService

class UserPublicController(
    private val userRegisterService: UserRegisterService,
    private val session: MindstockSession,
) : UserPublicRpcService {
    override suspend fun register(displayName: DisplayName): RpcResult<Profile, RpcError> =
        rpcBoundary(session) {
            userRegisterService.register(session.identity, displayName)
        }
}
```

`CatalogController.kt` の `search` のような 1 行メソッドも同様: `tx(database, session) { RpcResult.Ok(catalogItemService.search(query, limit)) }` → `rpcBoundary(session) { catalogItemService.search(query, limit) }`。

- [ ] **Step 3: コミット(コンパイルはまだ DI 未追従で失敗する)**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/presentation
git commit -m "refactor(api): Controller を rpcBoundary 化、Database 依存を除去

各メソッドを rpcBoundary(session){} で包み、ドメイン値を返す(RpcResult.Ok は
boundary が付与)。DI の追従は後続コミット。"
```

---

## Task 7: ControllerFactory と DI を追従

**Files:**
- Modify: `presentation/rpc/user/UserPublicControllerFactory.kt`(変更不要 — `session` のみ受ける fun interface。確認のみ)
- Modify: `configuration/di/DependenciesConfiguration.kt`

> ControllerFactory(`fun interface ... { fun create(session): XxxController }`)は `Database` を引数に取っていない(factory closure 内で `db` を捕捉していた)。よって interface 自体は変更不要。変更は DI の closure のみ。

- [ ] **Step 1: DI の DataSource provide に `resolve<Database>()` を渡す**

`DependenciesConfiguration.kt` の Repository ブロックを以下に変更:

```kotlin
        // Repository (10) — 各 DataSource は Database をコンストラクタで受ける
        provide<UserRepository> { UserDataSource(resolve()) }
        provide<UserRegisterRepository> { UserRegisterDataSource(resolve()) }

        provide<HouseholdRepository> { HouseholdDataSource(resolve()) }
        provide<HouseholdRegisterRepository> { HouseholdRegisterDataSource(resolve()) }

        provide<CatalogItemRepository> { CatalogItemDataSource(resolve()) }
        provide<CatalogItemRegisterRepository> { CatalogItemRegisterDataSource(resolve()) }

        provide<ProductRepository> { ProductDataSource(resolve()) }
        provide<ProductRegisterRepository> { ProductRegisterDataSource(resolve()) }

        provide<StockRepository> { StockDataSource(resolve(), resolve()) }
        provide<StockRegisterRepository> { StockRegisterDataSource(resolve()) }
```

> `StockDataSource(resolve(), resolve())` の第 1 引数は `ProductRepository`、第 2 引数は `Database`。Ktor DI の `resolve()` は型推論で解決される。順序はコンストラクタ宣言順(`productRepository`, `database`)。

- [ ] **Step 2: DI の ControllerFactory closure から `db` 解決・引き渡しを削除**

Controller Factory ブロックを以下に変更(各 `val db = resolve<Database>()` と `db` 引数を削除):

```kotlin
        // Controller Factory (30) — per-WS-connection 単位で Controller を組み立てる
        provide<UserPublicControllerFactory> {
            val urs = resolve<UserRegisterService>()
            UserPublicControllerFactory { session -> UserPublicController(urs, session) }
        }
        provide<UserControllerFactory> {
            val urs = resolve<UserRegisterService>()
            UserControllerFactory { session -> UserController(urs, session) }
        }
        provide<HouseholdControllerFactory> {
            val hs = resolve<HouseholdService>()
            val hrs = resolve<HouseholdRegisterService>()
            val us = resolve<UserService>()
            HouseholdControllerFactory { session -> HouseholdController(hs, hrs, us, session) }
        }
        provide<CatalogControllerFactory> {
            val cs = resolve<CatalogItemService>()
            val crs = resolve<CatalogItemRegisterService>()
            CatalogControllerFactory { session -> CatalogController(cs, crs, session) }
        }
        provide<ProductControllerFactory> {
            val ps = resolve<ProductService>()
            val prs = resolve<ProductRegisterService>()
            val hs = resolve<HouseholdService>()
            val cs = resolve<CatalogItemService>()
            ProductControllerFactory { session -> ProductController(ps, prs, hs, cs, session) }
        }
        provide<StockControllerFactory> {
            val ss = resolve<StockService>()
            val srs = resolve<StockRegisterService>()
            val ps = resolve<ProductService>()
            val hs = resolve<HouseholdService>()
            StockControllerFactory { session -> StockController(ss, srs, ps, hs, session) }
        }
```

`import org.jetbrains.exposed.v1.jdbc.Database` は、Repository provide で `resolve()` の型推論に使われるだけなら不要になる場合がある。コンパイルエラーが出なければ削除、`resolve<Database>()` の明示が残るなら維持。本変更では明示 `resolve<Database>()` を消したので **import は削除**。

- [ ] **Step 3: コミット**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/di
git commit -m "refactor(api): DI を追従 — DataSource に Database 注入、Controller から db 除去

DataSource は resolve() で Database を受け取り、ControllerFactory closure から
db 捕捉を削除。"
```

---

## Task 8: `tx()` を削除、`MindstockAuthPlugin` にコメント追記

**Files:**
- Delete: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/transaction/Transaction.kt`
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/MindstockAuthPlugin.kt`

- [ ] **Step 1: `tx()` の参照が残っていないことを確認**

Run: `git grep -n "configuration.transaction.tx\|tx(database" backend/api/src/main`
Expected: 出力なし(プロダクションコードから `tx()` 参照が消えている)。

> もし残っていれば Task 6 の変換漏れ。先に潰す。

- [ ] **Step 2: `Transaction.kt` を削除**

```bash
git rm backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/transaction/Transaction.kt
```

`configuration/transaction/` ディレクトリが空になるなら、空ディレクトリは git 管理外なので放置でよい。

- [ ] **Step 3: `MindstockAuthPlugin` の `newSuspendedTransaction` にコメント追記**

L85-92 付近の `newSuspendedTransaction(db = database) { ... }` の直前に、なぜここだけ tx を直接張るかを明記(将来の混乱防止):

```kotlin
            // 認証は WS upgrade 時の処理で RPC 境界(rpcBoundary)の外。よって
            // 「tx は DataSource 内」原則の対象外とし、ここでは findProfileByAuthIdentity を
            // 呼ぶための独立した小さな tx を直接張る。
            // userId が null になるのは「JWT 検証は通ったが対応する User が DB に未登録」のケース。
            val userId =
                newSuspendedTransaction(db = database) {
                    try {
                        userRepository.findProfileByAuthIdentity(identity).userId
                    } catch (e: ResourceNotFoundException) {
                        null
                    }
                }
```

> **注意:** `userRepository.findProfileByAuthIdentity` は Task 1 で `suspend` 化済み。`newSuspendedTransaction` の中で suspend 関数を呼ぶのは合法。ただし `UserDataSource.findProfileByAuthIdentity` が**内部でも** `newSuspendedTransaction` を張るため、ここはネスト tx になる(`useNestedTransactions = true` で吸収)。動作はするが、よりクリーンにするなら認証層は `userRepository` を介さず生クエリにする手もある。本計画では現状の委譲を維持しネストを許容する。

- [ ] **Step 4: コミット**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/MindstockAuthPlugin.kt
git commit -m "refactor(api): tx() を削除し rpcBoundary に一本化

認証層の newSuspendedTransaction は RPC 境界外のため対象外。理由をコメントで明記。"
```

---

## Task 9: `RequireUnregisteredUserPlugin` を追加し routing に適用(TDD)

**Files:**
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/RequireUnregisteredUserPlugin.kt`
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/routing/RoutingConfiguration.kt`
- Create/Modify: E2E テスト `backend/api/src/test/kotlin/net/brightroom/mindstock/e2e/user/UserPublicRpcServiceE2eTest.kt`

**設計:** `/user/public` route に install。`session.userId != null`(=登録済み)なら `409 Conflict` を返して register に到達させない。これにより「登録済みユーザーが**再接続して** register を呼ぶ」経路を断つ。

> **重要な前提(接続単位 guard):** `MindstockSession` は WS 接続確立時に 1 回だけ組み立てられ、`RequireUnregisteredUserPlugin`(`onCall`)も**接続ごとに 1 回**しか走らない。よって guard が効くのは「register 成功 → 切断 → 再接続(JWT が登録済み userId に解決される)→ register」という**別接続の再試行**ケース。
>
> **同一接続で register を 2 回**呼ぶ退行ケース(既存の pinning テストがやっている)は guard を通過し、2 回目は DataSource のメソッド内 tx で `users.zitadel_sub` UNIQUE 違反 → ロールバック → `rpcBoundary` が `Throwable` を捕捉して `RpcError.Internal` を返す。これは**現状(`tx()` 時代)と同じ挙動**であり、退行クライアントへの応答として許容する。したがって既存 pinning テストの**期待値(`Internal`)は変更不要**(コメント文言のみ `tx()` → `rpcBoundary` に更新)。
>
> guard の効果は**新規テストで別接続の再試行**を検証する(Step 4)。

- [ ] **Step 1: プラグインを実装**

`RequireRegisteredUserPlugin.kt` を参考に逆の guard を作る:

```kotlin
package net.brightroom.mindstock.configuration.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.response.respond

/**
 * Route subtree に install することで「未登録 User しか通さない」境界を作る。
 * MindstockAuthPlugin が組み立てた MindstockSession を見て userId != null(登録済み)なら 409。
 *
 * 用途: /user/public/register は未登録ユーザー専用。登録済みユーザーの再 register を
 * routing 層で遮断し、users.zitadel_sub の UNIQUE 違反を未然に防ぐ。
 */
val RequireUnregisteredUserPlugin =
    createRouteScopedPlugin(name = "RequireUnregisteredUser") {
        onCall { call ->
            val session = call.attributes.getOrNull(MindstockSessionKey)
            if (session == null || session.userId != null) {
                call.respond(HttpStatusCode.Conflict)
            }
        }
    }
```

- [ ] **Step 2: routing に install**

`RoutingConfiguration.kt` の `/user/public` ブロックを変更:

```kotlin
            // JWT 有効ならよい(未登録 OK)。ただし登録済みは register に到達させない
            route("/user/public") {
                install(RequireUnregisteredUserPlugin)
                rpc {
                    val session = call.attributes[MindstockSessionKey]
                    registerService<UserPublicRpcService> { userPublicFactory.create(session) }
                }
            }
```

import を追加: `import net.brightroom.mindstock.configuration.auth.RequireUnregisteredUserPlugin`。

- [ ] **Step 3: 既存 pinning テストのコメント文言のみ更新(期待値は維持)**

`UserPublicRpcServiceE2eTest.kt` の「register returns Err(Internal) on duplicate sub」テストは **同一接続で register を 2 回**呼ぶ退行ケース。guard は接続単位で発火するため、このケースは guard を通過し 2 回目は DataSource の tx で UNIQUE 違反 → `rpcBoundary` が `Internal` に変換する。**期待値(`Internal`)・テスト本体は変更しない。** コメントの `tx() の catch で` を `rpcBoundary の catch で` に文言更新するのみ:

```kotlin
        // Pinning test for the server→client error propagation contract.
        // 重複 sub による DB UNIQUE 制約違反は rpcBoundary の catch で RpcError.Internal に変換される。
        // 同一接続での 2 回 register は接続単位 guard を通過するため、この退行ケースは Internal を返す。
        // 別接続の再試行(登録済み JWT で再接続)は RequireUnregisteredUserPlugin が 409 で弾く(別テストで検証)。
        test("register returns Err(Internal) on duplicate sub and pipeline stays usable") {
```

- [ ] **Step 4: 新規テスト — 別接続の再試行が guard で 409 になることを検証**

`MindstockAuthPlugin` は JWT の sub から `userRepository.findProfileByAuthIdentity(identity).userId` を引いて `session.userId` を埋める。よって「同一 sub で 1 回 register → **新しい RPC クライアント接続**を同じ token で開く」と、2 回目の接続では `session.userId != null` になり `RequireUnregisteredUserPlugin` が 409 を返して WS upgrade が失敗する。

`UserPublicRpcServiceE2eTest.kt` に新規ケースを追加(ハーネスの `authenticatedRpcClientWithToken` を利用):

```kotlin
        test("registered user re-registering on a fresh connection is rejected by guard") {
            e2eTest {
                val sub = "retry-subject"
                val token = TestJwtIssuer.issue(subject = sub)

                // 1 回目: 未登録なので register 成功
                val first =
                    authenticatedRpcClientWithToken(token = token, path = "user/public")
                        .withService<UserPublicRpcService>()
                first.register(DisplayName("Alice"))

                // 2 回目: 別接続を同じ token で開く → MindstockAuthPlugin が userId を解決
                //   → RequireUnregisteredUserPlugin が 409 で WS upgrade を弾く
                //   → RPC 呼び出しが確立できず例外になる
                shouldThrowAny {
                    val second =
                        authenticatedRpcClientWithToken(token = token, path = "user/public")
                            .withService<UserPublicRpcService>()
                    second.register(DisplayName("Bob"))
                }
            }
        }
```

import: `import io.kotest.assertions.throwables.shouldThrowAny`。

> **実装ノート:** `authenticatedRpcClientWithToken` / `withService` は既存ハーネス(`E2eContext`)の API。`shouldThrowAny` の対象が「接続確立時の例外」か「register 呼び出し時の例外」かは Krpc クライアントの遅延接続の挙動による。どちらでも `shouldThrowAny { 接続 + register }` のブロック全体で捕捉できるよう、接続確立と `register` 呼び出しの両方をブロック内に入れている。もし 409 が例外でなく別形で観測される場合(Krpc が upgrade 失敗を別経路で返す)は、ハーネスの実挙動に合わせて assert を調整する。

- [ ] **Step 5: E2E テストを実行**

Run: `./gradlew integrationTest --tests "*UserPublicRpcServiceE2eTest*" -q`(E2E は `@Tags("integration")`)
Expected: PASS。pinning(同一接続 2 回 = Internal)維持 / 新規(別接続再試行 = guard 拒否)成功。

- [ ] **Step 6: コミット**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/RequireUnregisteredUserPlugin.kt backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/routing/RoutingConfiguration.kt backend/api/src/test/kotlin/net/brightroom/mindstock/e2e/user/UserPublicRpcServiceE2eTest.kt
git commit -m "feat(api): RequireUnregisteredUserPlugin で別接続の再 register を遮断

登録済みセッションは /user/public で 409。別接続の再試行による
users.zitadel_sub UNIQUE 違反を routing 層で防ぐ。同一接続 2 回の退行ケースは
従来どおり Internal(pinning テスト維持、コメントのみ更新)。別接続再試行の
guard 拒否を新規 E2E で検証。"
```

---

## Task 10: 既存テストの全面追従とフルビルド

**Files:**
- Delete/Modify: `backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/transaction/TxWithGuardTest.kt`
- Modify: 各 Controller テスト(`presentation/rpc/**/*ControllerTest.kt` × 6)
- Modify: その他の E2E テスト(catalog / product / household / stock / user)

- [ ] **Step 1: `TxWithGuardTest` を削除(`RpcBoundaryTest` が後継)**

`tx()` を検証していた `TxWithGuardTest.kt` は `rpcBoundary` のテスト(Task 5)に置き換わったため削除する。

```bash
git rm backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/transaction/TxWithGuardTest.kt
```

- [ ] **Step 2: Controller テストを追従**

各 `*ControllerTest.kt` で、Controller のコンストラクタから `database` 引数を除去(`StockController(stockService, ..., session)`、`db` を渡さない)。Service モックは `suspend` 関数になったため、MockK の `coEvery { ... }` / `coVerify { ... }`(`every`/`verify` ではなく)を使う。Controller メソッドは `suspend` なので呼び出しは `runBlocking { controller.xxx(...) }`。

変換例(StockControllerTest の 1 ケース):

```kotlin
// before:
//   val controller = StockController(stockService, stockRegisterService, productService, householdService, session, database)
//   every { productService.findById(productId) } returns product
//   val result = runBlocking { controller.get(productId) }

// after:
import io.mockk.coEvery
import io.mockk.coVerify

val controller = StockController(stockService, stockRegisterService, productService, householdService, session)
coEvery { productService.findById(productId) } returns product
coEvery { stockService.get(product) } returns stock
val result = runBlocking { controller.get(productId) }
```

`every`→`coEvery`、`verify`→`coVerify` への置換を全 Controller テストに適用。`database`(`mockk<Database>()` 等)の宣言と引き渡しを削除。

- [ ] **Step 3: 残りの E2E テストを追従**

`E2eTestSupport.kt` が DataSource を直接組み立てている場合(`E2eContext(client, database, dataSource)` の確認結果より)、DataSource コンストラクタが `Database` を取るようになった点に追従。E2E 内で Repository/DataSource を直接使うヘルパーがあれば `runBlocking` + 新コンストラクタ引数に合わせる。各 E2E(`CatalogRpcServiceE2eTest` 等)はクライアント経由で RPC を叩くだけなら本体変更は少ないが、コンパイルエラーを潰す。

- [ ] **Step 4: フルビルド + 統合テスト**

Run: `./gradlew :backend:api:build -q`
Expected: PASS。

Run: `./gradlew integrationTest -q`
Expected: PASS。

Run: `./gradlew build -x :frontend:wasmJsBrowserTest -q`(frontend WasmJs は OOM するため除外)
Expected: PASS。

> ローカルメモ(memory: local-build-tips): 統合テストは pool キャップ済で普通に流せる。frontend WasmJs は OOM るので除外。

- [ ] **Step 5: コミット**

```bash
git add -A backend/api/src/test
git commit -m "test(api): tx() 廃止に全テストを追従

TxWithGuardTest を削除(RpcBoundaryTest が後継)。Controller テストは
coEvery/coVerify + database 引数除去。E2E は新 DataSource コンストラクタに追従。"
```

---

## Task 11: rule ドキュメントを更新

**Files:**
- Modify: `.claude/rules/rpc-and-transactions.md`
- Modify: `.claude/rules/software-architecture.md`

- [ ] **Step 1: `rpc-and-transactions.md` の `tx()` 節を書き換え**

`### tx() ヘルパー` 節を以下の趣旨に全面改訂:

- 見出しを `### トランザクション境界` に変更
- 「DB を触る RPC method は `tx(database) { }` で包む」を削除
- 新ルール: 「**トランザクション境界は DataSource メソッド内**。各 DataSource は `Database` をコンストラクタで受け、メソッド本体を `newSuspendedTransaction(db = database) { }` で囲む。Repository interface / Service は `suspend`。」
- 新ルール: 「**presentation は `rpcBoundary(session) { }`**(`configuration/rpc/RpcBoundary.kt`)。transaction は張らず、guard + 例外→RpcError 変換 + log + supervisorScope のみ。block はドメイン値を返し、成功時に `RpcResult.Ok` で包む。」
- 「DB を触らない RPC method」も同じく `rpcBoundary` で包む(guard/log を効かせるため)旨を明記
- 認証層(`MindstockAuthPlugin`)は RPC 境界外なので例外的に直接 tx を張る、と補足
- `## How to apply` の `tx()` コード例を `rpcBoundary` 版に差し替え(Task 6 の `StockController` を縮約して使用)

- [ ] **Step 2: `software-architecture.md` の DataSource 節を書き換え(現行と逆向きの重要変更)**

`### DataSource(infrastructure)` の以下の行を改訂:

- 旧: 「実装内では `transaction {}` を書かない(Ktor plugin または `tx()` で境界管理)」
- 新: 「実装内で `newSuspendedTransaction(db = database) { }` を張り、メソッド = transaction 境界とする。`Database` はコンストラクタで受ける。メソッドは `suspend`。」

`presentation (rpc Controller, RpcError, MindstockSession)` 図中の `configuration` 説明の「tx ヘルパー」を「rpcBoundary(presentation 境界)」に更新。

- [ ] **Step 3: コミット**

```bash
git add .claude/rules/rpc-and-transactions.md .claude/rules/software-architecture.md
git commit -m "docs(rules): tx() 廃止に rule を追従

トランザクション境界は DataSource メソッド内(newSuspendedTransaction)。
presentation は rpcBoundary。software-architecture の DataSource 節は
現行と逆向き(transaction を書く)に更新。"
```

---

## Task 12: 最終検証

- [ ] **Step 1: `tx(` の残存がないことを確認**

Run: `git grep -n "configuration.transaction" backend/ ; git grep -n "fun tx<\|= tx(database" backend/`
Expected: テストの生 seed 用 `RepositoryTestContext.tx` 以外に `tx(database, session)` 形式の参照が無いこと。

- [ ] **Step 2: フルビルド**

Run: `./gradlew build -x :frontend:wasmJsBrowserTest -q`
Expected: PASS

- [ ] **Step 3: 統合テスト**

Run: `./gradlew integrationTest -q`
Expected: PASS

- [ ] **Step 4: spec との突き合わせ**

design spec(`docs/superpowers/specs/2026-05-30-transaction-boundary-redesign-design.md`)の §3 判断 1〜4 がすべて実装されたことを確認:
- 判断 1(tx を DataSource へ)= Task 2
- 判断 2(suspend 伝播)= Task 1〜4
- 判断 3(rpcBoundary)= Task 5〜6
- 判断 4(register guard)= Task 9

---

## Self-Review チェック結果

**Spec coverage:**
- §3 判断 1(tx を DataSource へ)→ Task 2 ✅
- §3 判断 2(suspend + newSuspendedTransaction)→ Task 1, 2, 3, 4 ✅
- §3 判断 3(rpcBoundary 分離)→ Task 5, 6 ✅
- §3 判断 4(RequireUnregisteredUserPlugin)→ Task 9 ✅
- §4.1（core 変更）→ Task 1〜4 ✅
- §4.2（api 変更, rpcBoundary, guard, routing, auth コメント）→ Task 5〜9 ✅
- §4.3（tx() 削除, useNestedTransactions 維持）→ Task 8（削除）/ 落とし穴 §2（維持を明記）✅
- §5（テスト方針）→ Task 4, 10 ✅
- §8（rule 更新)→ Task 11 ✅

**型整合性:** `rpcBoundary(session: MindstockSession, block: suspend () -> T): RpcResult<T, RpcError>` の名前・シグネチャは Task 5(定義)・Task 6(使用)で一致。DataSource コンストラクタは Task 2(定義)・Task 4(テスト)・Task 7(DI)で `(... , database: Database)` 一致。`StockDataSource(productRepository, database)` の 2 引数順序は Task 2・4・7 で一致。

**Placeholder スキャン:** 「TBD」「TODO」「適切に」等は無し。E2E ハーネス(`E2eTestSupport` / `E2eContext`)の接続確立 API(`authenticatedRpcClientWithToken` / `withService` / `publicRpcClient`)は確認済みで、Task 9 のテストコードはその API に沿って記述。

**接続単位 guard の罠(確認済み):** `MindstockSession` と `RequireUnregisteredUserPlugin` の `onCall` は WS 接続ごとに 1 回。よって同一接続での 2 回 register は guard を通過し `Internal`(既存 pinning 維持)、別接続の再試行のみ guard が 409 で弾く(新規テスト)。Task 9 はこの 2 ケースを分離して扱う。
