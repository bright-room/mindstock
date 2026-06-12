# フェーズ 3: infrastructure 品質(テスト安全網 → 性能)実装プラン

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (推奨) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** テストゼロの DataSource(DB)層に Hydration round-trip の統合テストで安全網を張り、その網の下で `StockDataSource.listByHousehold` の 2N+1 と `HouseholdDataSource.listByResident` の 1+3N を一括取得に解消する。併せて membership insert の重複を helper 化し、`Created.now()` の取得タイミングを「原則 tx 内」へ統一する。

**Architecture:** 統合テストは **testcontainers ではなく**、`mise` が用意する live `mindstock_test` DB(`TEST_DB_URL`/`TEST_DB_USER`/`TEST_DB_PASSWORD`、既定 `jdbc:postgresql://localhost:5432/mindstock_test` / `mindstock` / `mindstock`)に当てる(既存 `ProductImageTransferTest` が Garage に env で当てるのと同型)。`mindstock_test` は空 DB のため、テストフィクスチャ `TestDatabase`(`src/testFixtures`)が Flyway migrate でスキーマを作り、`clean()` で各テスト前に全テーブルを TRUNCATE する。テストは production の Register/Read DataSource を通して save→load し、Hydration(window 関数による最新行抽出・集約組み立て・sealed mapping)の正しさを検証する。N+1 解消は「テストが書き換え前後で green」を硬いゲートにし、クエリ数は Exposed の SQL ログで before/after を手動記録する。

**Tech Stack:** Kotlin / Exposed v1(jdbc)/ Flyway / HikariCP / Kotest FunSpec(`@Tags("integration")`)/ PostgreSQL

---

## 前提・実行環境

- **DB が必要**: 本フェーズの統合テストは `mise run up`(または `docker compose up -d` 済み)で `mindstock-postgres` が healthy であること。確認: `docker exec mindstock-postgres psql -U mindstock -d mindstock_test -tAc "select 1"` が `1` を返す。
- **env**: `mise` 配下で実行すると `TEST_DB_*` が注入される。素の `./gradlew` 直叩きでは `TestDatabase` が既定値(上記)へフォールバックするので、ローカル postgres が 5432 で上がっていれば env なしでも回る。
- **実行コマンド**: `./gradlew :backend:core:integrationTest`(通常の `./gradlew :backend:core:test` は `kotest.tags.exclude=integration` で本フェーズのテストを除外するので、リファクタの安全網としては integrationTest を回す)。
- 各タスクのコミットメッセージに issue/PR 番号を書かない(working agreement)。

### 既知の制約: 共有 DB の TRUNCATE と並列実行(フェーズ 5-5 で対処)

`TestDatabase.clean()` は単一の `mindstock_test` を全表 TRUNCATE する共有 DB 方式。`org.gradle.parallel=true`(workers.max=4)のため、**複数モジュールの `integrationTest` が同一 DB に同時 TRUNCATE すると flaky になりうる**。本フェーズ時点では実害なし(`backend:api:integrationTest` は `@Tags("integration")` テスト 0 件の空実行で DB に触れず、Kotest は並列未設定で JVM 内 spec は逐次)。ただし将来 `api` に DB 統合テストが入り `./gradlew integrationTest`(name-matching で両モジュール起動)を並列で回すと顕在化する。**フェーズ 5-5 の `integrationTest` convention 統合時に、モジュール横断の直列化(`mustRunAfter`)かモジュール別スキーマ分離で対処する**(本フェーズでは api が空のため先回りガードは入れない)。

## ファイル構成(本フェーズで作成/変更)

**作成:**
- `backend/core/src/testFixtures/kotlin/net/brightroom/mindstock/testfixtures/TestDatabase.kt` — 統合テスト用 DB 接続(Hikari)+ Flyway migrate + 全テーブル TRUNCATE
- `backend/core/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/TestDatabaseSmokeTest.kt` — フィクスチャ疎通(migrate + clean が通ること)
- `backend/core/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/stock/StockDataSourceTest.kt` — Stock round-trip + `listByHousehold` 複数商品(3-2 安全網)
- `backend/core/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/household/HouseholdDataSourceTest.kt` — Household round-trip + `listByResident` 複数世帯(3-3 安全網)
- `backend/core/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/product/ProductDataSourceTest.kt` — Product round-trip(最新 revision / wanted / archived)
- `backend/core/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/invitation/InvitationDataSourceTest.kt` — Invitation round-trip(issue→有効 / revoke→無効)

**変更:**
- `backend/core/build.gradle.kts` — `integrationTest` タスクに `TEST_DB_*` env 転送を追加
- `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/stock/StockDataSource.kt` — `listByHousehold` の 2N+1 を一括取得へ(3-2)
- `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/household/HouseholdDataSource.kt` — `listByResident` の 1+3N を一括取得へ(3-3)
- `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/household/HouseholdRegisterDataSource.kt` — 重複 insert の helper 化(3-4)+ `registerHousehold` の `Created.now()` を tx 内へ(3-5)

## スコープ確定(マスタープランからの訂正)

- **3-5 の実スコープは 1 箇所のみ**: `infrastructure/datasource/` 全 `*DataSource.kt` の `Created.now()` を精査した結果、tx 外取得は (a) `HouseholdRegisterDataSource.registerHousehold:25`、(b) `InvitationRegisterDataSource.issue:24`、(c) `ProductRegisterDataSource.insertProductAndRevision:79` の 3 箇所。うち **(c) は呼び出し元 `registerAdopted`/`registerCustom` が共に `transaction{}` 内で呼ぶ private helper のため実質 tx 内(違反でない)**、**(b) は retry ループでトランザクションを跨いで同一時刻を使う、`backend-software-architecture.md` が明示する許容例外**。したがって **実際に直すのは (a) `registerHousehold` のみ**。マスタープランの「現状 `issue` のみ tx 外」という記述は不正確だった。

---

## Task 1: 統合テスト基盤(Gradle env 転送 + TestDatabase フィクスチャ)

DB 接続・migrate・TRUNCATE を 1 箇所に閉じ込め、以降の全テストが `TestDatabase.database` と `TestDatabase.clean()` だけ使えばよい状態にする。

**Files:**
- Modify: `backend/core/build.gradle.kts`(`integrationTest` タスク定義)
- Create: `backend/core/src/testFixtures/kotlin/net/brightroom/mindstock/testfixtures/TestDatabase.kt`
- Test: `backend/core/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/TestDatabaseSmokeTest.kt`

- [ ] **Step 1: `integrationTest` に `TEST_DB_*` 転送を追加**

`backend/core/build.gradle.kts` の `val integrationTest by tasks.registering(Test::class) { ... }` 内、`STORAGE_*` を転送している箇所の直後に以下を追記する(現状 `STORAGE_*` しか転送しておらず DB 系テストに env が渡らない):

```kotlin
    // app と同一の STORAGE_* env(external.storage.* / application.yaml デフォルト)を test JVM へ転送する。
    listOf("STORAGE_ENDPOINT", "STORAGE_BUCKET", "STORAGE_ACCESS_KEY", "STORAGE_SECRET_KEY")
        .forEach { key -> System.getenv(key)?.let { environment(key, it) } }
    // DataSource(DB)統合テスト用の TEST_DB_* も転送する(未設定なら TestDatabase が既定値へフォールバック)。
    listOf("TEST_DB_URL", "TEST_DB_USER", "TEST_DB_PASSWORD")
        .forEach { key -> System.getenv(key)?.let { environment(key, it) } }
```

- [ ] **Step 2: `TestDatabase` フィクスチャを作成**

`backend/core/src/testFixtures/kotlin/net/brightroom/mindstock/testfixtures/TestDatabase.kt`:

```kotlin
package net.brightroom.mindstock.testfixtures

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.statements.api.ExposedConnection
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * DataSource(DB)統合テスト用の共有 DB ハンドル。
 *
 * testcontainers は使わず、mise が用意する live `mindstock_test`(空 DB)に当てる。
 * 初回アクセスで Flyway が `classpath:db/migration`(V1__init.sql)を流してスキーマを作る。
 * 各テストの先頭で [clean] を呼び、アプリ全テーブルを TRUNCATE して独立させる。
 */
object TestDatabase {
    private val url = System.getenv("TEST_DB_URL") ?: "jdbc:postgresql://localhost:5432/mindstock_test"
    private val user = System.getenv("TEST_DB_USER") ?: "mindstock"
    private val password = System.getenv("TEST_DB_PASSWORD") ?: "mindstock"

    // FK 依存順の逆(子 → 親)で TRUNCATE 列挙。RESTART IDENTITY で BIGSERIAL も 1 に戻す。
    // CASCADE を付けるので厳密な順序依存はないが、対象を明示して flyway_schema_history を守る。
    private val truncateSql =
        """
        TRUNCATE TABLE
            stock_movements,
            product_revisions,
            product_wanted_events,
            product_barcodes,
            product_catalog_links,
            products,
            catalog_items,
            invitation_validity_events,
            invitations,
            household_membership_events,
            household_names,
            households,
            resident_display_names,
            resident_auth_identities,
            residents
        RESTART IDENTITY CASCADE
        """.trimIndent()

    val database: Database by lazy {
        val dataSource =
            HikariDataSource(
                HikariConfig().apply {
                    driverClassName = "org.postgresql.Driver"
                    jdbcUrl = url
                    username = user
                    password = this@TestDatabase.password
                    maximumPoolSize = 4
                    isAutoCommit = false
                },
            )
        Flyway
            .configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
        Database.connect(
            datasource = dataSource,
            databaseConfig = DatabaseConfig.invoke { useNestedTransactions = true },
        )
    }

    /** アプリ全テーブルを空にする(flyway_schema_history は残す)。各テスト先頭で呼ぶ。 */
    fun clean() {
        transaction(database) {
            exec(truncateSql)
        }
    }
}
```

> 注: `exec(String)` は Exposed の `Transaction.exec` 拡張。import 不要(`transaction{}` レシーバ上のメンバ)。もし解決できない場合は `org.jetbrains.exposed.v1.jdbc.exec` を import する。`ExposedConnection` import が未使用なら削除してよい(コンパイラ警告に従う)。

- [ ] **Step 3: 疎通スモークテストを作成**

`backend/core/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/TestDatabaseSmokeTest.kt`:

```kotlin
package net.brightroom.mindstock.infrastructure.datasource

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.infrastructure.datasource.schemas.ResidentsTable
import net.brightroom.mindstock.testfixtures.TestDatabase
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

@Tags("integration")
class TestDatabaseSmokeTest :
    FunSpec({
        beforeTest { TestDatabase.clean() }

        test("migrate + clean が通り residents が空で読める") {
            transaction(TestDatabase.database) {
                ResidentsTable.selectAll().count()
            } shouldBe 0L
        }
    })
```

- [ ] **Step 4: 統合テストを実行して green を確認**

Run: `./gradlew :backend:core:integrationTest --tests "*TestDatabaseSmokeTest"`
Expected: PASS(`mindstock_test` に 15 テーブル + `flyway_schema_history` が作られ、smoke が green)。

確認補助: `docker exec mindstock-postgres psql -U mindstock -d mindstock_test -tAc "select count(*) from information_schema.tables where table_schema='public'"` が 0 → 16 に増えていること。

- [ ] **Step 5: コミット**

```bash
git add backend/core/build.gradle.kts \
  backend/core/src/testFixtures/kotlin/net/brightroom/mindstock/testfixtures/TestDatabase.kt \
  backend/core/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/TestDatabaseSmokeTest.kt
git commit -m "test(infra): DataSource 統合テスト基盤(TestDatabase + TEST_DB_* 転送)を追加"
```

---

## Task 2: Stock round-trip + listByHousehold 複数商品(3-2 の安全網)

`StockDataSource` の Hydration(movement 順序・netQuantity・actor displayName 解決・Correction sealed mapping)と、`listByHousehold` が **複数商品それぞれの movement を正しく束ねる** ことを固定する。次タスクの 2N+1 書き換えはこのテストを green に保ったまま行う。

**Files:**
- Test: `backend/core/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/stock/StockDataSourceTest.kt`

ヘルパ DataSource: `ResidentRegisterDataSource`(actor 作成)/ `HouseholdRegisterDataSource`(世帯)/ `ProductRegisterDataSource`(商品)/ `StockRegisterDataSource`(movement 追記)/ `ProductDataSource`(`StockDataSource` の依存)。

- [ ] **Step 1: 失敗するテストを書く**

`backend/core/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/stock/StockDataSourceTest.kt`:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.stock

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementIdentity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.infrastructure.datasource.household.HouseholdRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.product.ProductDataSource
import net.brightroom.mindstock.infrastructure.datasource.product.ProductRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.resident.ResidentRegisterDataSource
import net.brightroom.mindstock.testfixtures.TestDatabase

@Tags("integration")
class StockDataSourceTest :
    FunSpec({
        val db = TestDatabase.database
        val residents = ResidentRegisterDataSource(db)
        val households = HouseholdRegisterDataSource(db)
        val productWriter = ProductRegisterDataSource(db)
        val productReader = ProductDataSource(db)
        val movements = StockRegisterDataSource(db)
        val stocks = StockDataSource(db, productReader)

        beforeTest { TestDatabase.clean() }

        // actor を 1 人作って世帯に owner として登録し、その Resident を返す。
        fun setupActorAndHousehold(displayName: String): Pair<Resident, Household> {
            val actor =
                residents.registerResident(
                    AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-$displayName")),
                    DisplayName(displayName),
                )
            val household = Household.create(HouseholdName("わが家"), actor)
            households.registerHousehold(household)
            return actor to household
        }

        // 採用中の custom 商品を 1 つ作って永続化し、その Product を返す。
        fun adoptProduct(household: Household, name: String): Product {
            val product = Product.custom(ProductName(name), Barcode.Unlinked, ProductUnit("個"), MinimumStock(1))
            productWriter.registerCustom(product, household.id)
            return product
        }

        test("findByProduct: 補充+消費を追記すると netQuantity と actor displayName が引ける") {
            val (actor, household) = setupActorAndHousehold("たろう")
            val product = adoptProduct(household, "牛乳")
            movements.appendMovement(
                product.id,
                StockMovement.Replenishment(MovementIdentity.Pending, Quantity(5), OccurredAt.now(), actor, Note("入荷")),
            )
            movements.appendMovement(
                product.id,
                StockMovement.Consumption(MovementIdentity.Pending, Quantity(2), OccurredAt.now(), actor, Note("消費")),
            )

            val stock = stocks.findByProduct(product.id)
            stock.movements.list.size shouldBe 2
            stock.currentQuantity()() shouldBe 3 // 5 - 2(netQuantity 畳み込み)
            stock.movements.list.first().actor.profile.displayName() shouldBe "たろう"
        }

        test("listByHousehold: 商品が 3 つあっても各商品の movement が正しく束ねられる") {
            val (actor, household) = setupActorAndHousehold("はなこ")
            val milk = adoptProduct(household, "牛乳")
            val egg = adoptProduct(household, "卵")
            val bread = adoptProduct(household, "パン")
            movements.appendMovement(
                milk.id,
                StockMovement.Replenishment(MovementIdentity.Pending, Quantity(4), OccurredAt.now(), actor, Note("")),
            )
            movements.appendMovement(
                egg.id,
                StockMovement.Replenishment(MovementIdentity.Pending, Quantity(10), OccurredAt.now(), actor, Note("")),
            )
            // bread は movement なし

            val result = stocks.listByHousehold(household.id)
            result.list.size shouldBe 3
            val byProduct = result.list.associateBy { it.product.id }
            byProduct.getValue(milk.id).currentQuantity()() shouldBe 4
            byProduct.getValue(egg.id).currentQuantity()() shouldBe 10
            byProduct.getValue(bread.id).movements.list.size shouldBe 0
        }
    })
```

> 検証メモ: `stock.currentQuantity()()` は `Stock.currentQuantity(): NetQuantity` → `NetQuantity.invoke(): Int` の二段(`StockStatus`/`Archivability` と同じ呼び方)。`Stock` の実 API(`currentQuantity()` の有無・戻り型)を実装時に Read で確認し、ずれていれば `netQuantity` 等の実メソッドへ合わせる。`Product.custom` のシグネチャは `custom(name, barcode, unit, minimumStock)`(`domain/.../inventory/product/Product.kt:46`)。

- [ ] **Step 2: テストを実行して green を確認(現状コードに対する characterization)**

Run: `./gradlew :backend:core:integrationTest --tests "*StockDataSourceTest"`
Expected: PASS。**これは赤→緑ではなく、現状の N+1 実装の挙動を固定する characterization テスト**(次タスクのリファクタの安全網)。もし compile/assertion で落ちたら、ドメイン API のシグネチャ差(`currentQuantity` 等)を実コードに合わせて修正してから green にする。

- [ ] **Step 3: コミット**

```bash
git add backend/core/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/stock/StockDataSourceTest.kt
git commit -m "test(infra): StockDataSource の Hydration round-trip と listByHousehold 複数商品を固定"
```

---

## Task 3: StockDataSource.listByHousehold の 2N+1 を一括取得へ(3-2)

`listByHousehold` の per-product `loadMovements`(商品ごとに movement 取得 + actor 解決 = 2N)を、**movement を `product_id IN (...)` で一括取得 → groupBy、actor も全体一括**に置き換える。Task 2 のテストを green に保つ。

**Files:**
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/stock/StockDataSource.kt:56-100`

- [ ] **Step 1: 一括版 private メソッドを追加し `listByHousehold` を差し替える**

`StockDataSource` に以下の `loadMovementsByProducts` を追加し、`listByHousehold` を書き換える。既存の単一版 `loadMovements`(`findByProduct`/`findByMovement`/`historyOf` が使用)は残す。

`listByHousehold` を以下に置換(56-63 行):

```kotlin
    override fun listByHousehold(householdId: HouseholdId): Stocks =
        transaction(database) {
            val products = productDataSource.listByHousehold(householdId).list
            val movementsByProduct = loadMovementsByProducts(products.map { it.id })
            Stocks(products.map { p -> Stock(p, movementsByProduct[p.id] ?: StockMovements(emptyList())) })
        }
```

`loadMovements` の下に追加:

```kotlin
    /**
     * 複数 product の movement を 1 クエリで取得し product ごとに束ねる(N+1 回避)。
     * actor も全 movement 横断で一括解決する。movement の無い product はキーに現れない
     * (呼び出し側で空 [StockMovements] にフォールバックする)。
     */
    private fun loadMovementsByProducts(productIds: List<ProductId>): Map<ProductId, StockMovements> {
        if (productIds.isEmpty()) return emptyMap()
        val rows =
            StockMovementsTable
                .selectAll()
                .where { StockMovementsTable.productId inList productIds.map { it() } }
                // product ごとに id 昇順(= 追記順)。group 後も順序を保つため product_id, id でソート。
                .orderBy(
                    StockMovementsTable.productId to SortOrder.ASC,
                    StockMovementsTable.id to SortOrder.ASC,
                ).toList()
        if (rows.isEmpty()) return emptyMap()

        val actors = resolveActors(rows.map { it[StockMovementsTable.actorResidentId] }.toSet())

        return rows
            .groupBy { ProductId(it[StockMovementsTable.productId]) }
            .mapValues { (_, productRows) ->
                StockMovements(
                    productRows.map { row ->
                        val residentId = row[StockMovementsTable.actorResidentId]
                        val actor =
                            actors[residentId]
                                ?: throw ResourceNotFoundException("display name not found for resident: $residentId")
                        row.toStockMovement(actor)
                    },
                )
            }
    }
```

- [ ] **Step 2: actor 解決を共通 helper に抽出(単一版と一括版で共有)**

既存 `loadMovements` 内の「actor をバッチ解決」ブロック(78-89 行)を private `resolveActors` に抽出し、`loadMovements` と `loadMovementsByProducts` の両方から呼ぶ(DRY)。`loadMovements` の該当ブロックを `val actors = resolveActors(actorIds)` に置換:

```kotlin
    /** actor_resident_id 集合 → 最新 display_name 解決済み Resident の Map。 */
    private fun resolveActors(actorIds: Set<Uuid>): Map<Uuid, Resident> {
        if (actorIds.isEmpty()) return emptyMap()
        val (dnSub, dnRefs) = latestResidentDisplayNames()
        return ResidentsTable
            .join(dnSub, JoinType.INNER, onColumn = ResidentsTable.id, otherColumn = dnSub[ResidentDisplayNamesTable.residentId])
            .selectAll()
            .where { (ResidentsTable.id inList actorIds) and (dnSub[dnRefs.rn] eq 1L) }
            .associate { row ->
                val rid = row[ResidentsTable.id]
                rid to Resident(ResidentId(rid), ResidentProfile(DisplayName(row[dnSub[ResidentDisplayNamesTable.displayName]])))
            }
    }
```

`loadMovements`(68-100 行)の actor 解決部はこの helper 呼び出しに置き換え、`val actorIds = rows.map { ... }.toSet()` → `val actors = resolveActors(rows.map { it[StockMovementsTable.actorResidentId] }.toSet())` とする。

- [ ] **Step 3: テストが green のままか確認**

Run: `./gradlew :backend:core:integrationTest --tests "*StockDataSourceTest"`
Expected: PASS(Task 2 と同じ結果。書き換えで挙動が変わっていないこと)。

- [ ] **Step 4: クエリ数 before/after を手動記録**

`StockDataSourceTest` の `listByHousehold`(商品 3 つ)テストに一時的に SQL ロガーを仕込んでクエリ数を数える。`transaction` 内で `addLogger(StdOutSqlLogger)` を有効化するか、テストを `-i`(info)で回して SELECT 数を数える。簡便には実装メソッド内に一時 `addLogger`:

```kotlin
// 一時計測用(記録後に削除): listByHousehold の transaction(database) { の直後に
// addLogger(org.jetbrains.exposed.v1.jdbc.StdOutSqlLogger)
```

Run: `./gradlew :backend:core:integrationTest --tests "*StockDataSourceTest" --info 2>&1 | grep -ci "SELECT"`(目安)。
Expected: before(N+1 版)は商品数に比例、after は商品数に依らず一定(products 1 + movements 1 + actors 1 ≈ 3)。**計測用 `addLogger` はコミット前に必ず削除する**。記録はコミットメッセージ本文に残す。

- [ ] **Step 5: コミット**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/stock/StockDataSource.kt
git commit -m "$(cat <<'EOF'
perf(infra): StockDataSource.listByHousehold の 2N+1 を一括取得へ

movement を product_id IN (...) で一括取得し product ごとに groupBy、
actor も全体一括解決(resolveActors に抽出)。商品 N に対する
クエリ数を 2N+1 → 定数(products/movements/actors の 3)へ削減。
StockDataSourceTest(複数商品)で挙動非退行を確認。
EOF
)"
```

---

## Task 4: Household round-trip + listByResident 複数世帯(3-3 の安全網)

`HouseholdDataSource` の Hydration(最新世帯名 window・現所属メンバー window・メンバー displayName バッチ)と、`listByResident` が **複数世帯を正しく束ねる** ことを固定する。

**Files:**
- Test: `backend/core/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/household/HouseholdDataSourceTest.kt`

- [ ] **Step 1: 失敗しない characterization テストを書く**

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.household

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.infrastructure.datasource.resident.ResidentRegisterDataSource
import net.brightroom.mindstock.testfixtures.TestDatabase

@Tags("integration")
class HouseholdDataSourceTest :
    FunSpec({
        val db = TestDatabase.database
        val residents = ResidentRegisterDataSource(db)
        val writer = HouseholdRegisterDataSource(db)
        val reader = HouseholdDataSource(db)

        beforeTest { TestDatabase.clean() }

        fun resident(name: String): Resident =
            residents.registerResident(
                AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-$name")),
                DisplayName(name),
            )

        test("findById: owner + join したメンバーが role/displayName 付きで引ける") {
            val owner = resident("おーなー")
            val member = resident("めんばー")
            val household = Household.create(HouseholdName("我が家"), owner)
            writer.registerHousehold(household)
            writer.joinMember(household.id, member, HouseholdMemberRole.メンバー)

            val loaded = reader.findById(household.id)
            loaded.profile.name() shouldBe "我が家"
            loaded.members.list.size shouldBe 2
            loaded.members.roleOf(owner.id) shouldBe HouseholdMemberRole.世帯主
            loaded.members.roleOf(member.id) shouldBe HouseholdMemberRole.メンバー
        }

        test("findById: 最新の世帯名が引ける(rename 後)") {
            val owner = resident("おーなー2")
            val household = Household.create(HouseholdName("旧名"), owner)
            writer.registerHousehold(household)
            writer.appendHouseholdName(household.id, HouseholdName("新名"))

            reader.findById(household.id).profile.name() shouldBe "新名"
        }

        test("listByResident: 同一 resident が owner の世帯が 2 つあれば 2 件返る") {
            val owner = resident("ふたつもち")
            writer.registerHousehold(Household.create(HouseholdName("家A"), owner))
            writer.registerHousehold(Household.create(HouseholdName("家B"), owner))

            reader.listByResident(owner.id).list.size shouldBe 2
        }

        test("listByResident: 除外されたメンバーの世帯は返らない") {
            val owner = resident("のこる")
            val leaver = resident("でていく")
            val household = Household.create(HouseholdName("家C"), owner)
            writer.registerHousehold(household)
            writer.joinMember(household.id, leaver, HouseholdMemberRole.メンバー)
            writer.removeMember(household.id, leaver.id)

            reader.listByResident(leaver.id).list.size shouldBe 0
            reader.listByResident(owner.id).list.size shouldBe 1
        }
    })
```

> 検証メモ: `Members.roleOf(ResidentId): HouseholdMemberRole` の実 API(名前・戻り型)を実装時に Read で確認(`domain/.../household/member/`)。もし `roleOf` でなければ `loaded.members.list.first { it.resident.id == owner.id }.role` 等に合わせる。`Household.create(name, owner)` は `household/Household.kt:105`、`joinMember`/`appendHouseholdName`/`removeMember` は `HouseholdRegisterDataSource` の実 override。

- [ ] **Step 2: 実行して green を確認**

Run: `./gradlew :backend:core:integrationTest --tests "*HouseholdDataSourceTest"`
Expected: PASS(現状実装の characterization)。compile/assertion 差は実 API に合わせて解消。

- [ ] **Step 3: コミット**

```bash
git add backend/core/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/household/HouseholdDataSourceTest.kt
git commit -m "test(infra): HouseholdDataSource の Hydration round-trip と listByResident 複数世帯を固定"
```

---

## Task 5: HouseholdDataSource.listByResident の 1+3N を一括取得へ(3-3)

`listByResident` の per-household `hydrate`(世帯ごとに 名前 window + メンバー window + displayName バッチ = 3 クエリ)を、**`household_id IN (...)` の一括取得**に置き換える。Task 4 を green に保つ。

**Files:**
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/household/HouseholdDataSource.kt:41-158`

- [ ] **Step 1: 一括版を実装して `listByResident` を差し替える**

`listByResident`(41-48 行)を以下に置換:

```kotlin
    override fun listByResident(residentId: ResidentId): Households =
        transaction(database) {
            val ids = currentHouseholdIds(residentId)
            if (ids.isEmpty()) return@transaction Households(emptyList())
            val names = latestHouseholdNames(ids) // household_id -> HouseholdName(1 クエリ)
            val membersByHousehold = currentMembersByHouseholds(ids) // household_id -> List<HouseholdMember>(2 クエリ)
            Households(
                ids.map { id ->
                    val name = names[id] ?: throw ResourceNotFoundException("household not found: $id")
                    Household(id, HouseholdProfile(name), Members(membersByHousehold[id] ?: emptyList()))
                },
            )
        }
```

`latestHouseholdNames`(複数世帯の最新名を 1 クエリ)を追加。既存 `latestHouseholdName`(単一・`findById` 経由の `hydrate` が使用)は残す:

```kotlin
    /** 複数世帯の最新 household_name を 1 クエリで引く(window rn=1)。 */
    private fun latestHouseholdNames(ids: List<HouseholdId>): Map<HouseholdId, HouseholdName> {
        if (ids.isEmpty()) return emptyMap()
        val rn =
            rowNumber()
                .over()
                .partitionBy(HouseholdNamesTable.householdId)
                .orderBy(HouseholdNamesTable.id to SortOrder.DESC)
        val rnAlias = rn.alias("rn")
        val sub =
            HouseholdNamesTable
                .select(HouseholdNamesTable.householdId, HouseholdNamesTable.name, rnAlias)
                .where { HouseholdNamesTable.householdId inList ids.map { it() } }
                .alias("latest_names")
        return sub
            .selectAll()
            .where { sub[rnAlias] eq 1L }
            .associate {
                HouseholdId(it[sub[HouseholdNamesTable.householdId]]) to HouseholdName(it[sub[HouseholdNamesTable.name]])
            }
    }
```

`currentMembersByHouseholds`(複数世帯の現メンバーを 2 クエリ: membership window 1 + displayName バッチ 1)を追加。既存 `currentMembers`(単一)は残す:

```kotlin
    /** 複数世帯の現所属メンバー(rn=1 & status=所属)を household ごとに束ねる。displayName はバッチ。 */
    private fun currentMembersByHouseholds(ids: List<HouseholdId>): Map<HouseholdId, List<HouseholdMember>> {
        if (ids.isEmpty()) return emptyMap()
        val rn =
            rowNumber()
                .over()
                .partitionBy(HouseholdMembershipEventsTable.householdId, HouseholdMembershipEventsTable.residentId)
                .orderBy(HouseholdMembershipEventsTable.id to SortOrder.DESC)
        val rnAlias = rn.alias("rn")
        val mSub =
            HouseholdMembershipEventsTable
                .select(
                    HouseholdMembershipEventsTable.householdId,
                    HouseholdMembershipEventsTable.residentId,
                    HouseholdMembershipEventsTable.role,
                    HouseholdMembershipEventsTable.status,
                    rnAlias,
                ).where { HouseholdMembershipEventsTable.householdId inList ids.map { it() } }
                .alias("latest_members")

        // (householdId, residentId, role) を current メンバーだけ集める
        data class MemberRow(val householdId: HouseholdId, val residentId: ResidentId, val role: HouseholdMemberRole)
        val memberRows =
            mSub
                .selectAll()
                .where {
                    (mSub[rnAlias] eq 1L) and
                        (mSub[HouseholdMembershipEventsTable.status] eq MembershipStatus.所属)
                }.orderBy(mSub[HouseholdMembershipEventsTable.residentId] to SortOrder.ASC)
                .map {
                    MemberRow(
                        HouseholdId(it[mSub[HouseholdMembershipEventsTable.householdId]]),
                        ResidentId(it[mSub[HouseholdMembershipEventsTable.residentId]]),
                        it[mSub[HouseholdMembershipEventsTable.role]],
                    )
                }
        if (memberRows.isEmpty()) return emptyMap()

        // 全メンバーの最新 displayName を 1 クエリでバッチ解決
        val (dnSub, dnRefs) = latestResidentDisplayNames()
        val displayNames: Map<ResidentId, String> =
            ResidentsTable
                .join(dnSub, JoinType.INNER, onColumn = ResidentsTable.id, otherColumn = dnSub[ResidentDisplayNamesTable.residentId])
                .select(ResidentsTable.id, dnSub[ResidentDisplayNamesTable.displayName])
                .where {
                    (ResidentsTable.id inList memberRows.map { it.residentId() }.distinct()) and
                        (dnSub[dnRefs.rn] eq 1L)
                }.associate {
                    ResidentId(it[ResidentsTable.id]) to it[dnSub[ResidentDisplayNamesTable.displayName]]
                }

        return memberRows
            .groupBy { it.householdId }
            .mapValues { (_, rows) ->
                rows.map { mr ->
                    val displayName =
                        displayNames[mr.residentId]
                            ?: throw ResourceNotFoundException("resident display name not found: ${mr.residentId}")
                    HouseholdMember(Resident(mr.residentId, ResidentProfile(DisplayName(displayName))), mr.role)
                }
            }
    }
```

> import 追加: `HouseholdMemberRole`(`domain.model.household.member.HouseholdMemberRole`)を追加。既存の `rowNumber`/`alias`/`and`/`inList`/`JoinType`/`SortOrder` は流用。

- [ ] **Step 2: テストが green のままか確認**

Run: `./gradlew :backend:core:integrationTest --tests "*HouseholdDataSourceTest"`
Expected: PASS(挙動非退行)。

- [ ] **Step 3: クエリ数を手動記録**

`listByResident`(世帯 2 件)で計測。Task 3 Step 4 と同じく一時 `addLogger` で SELECT 数を確認 → before は 1+3N、after は world_ids 1 + names 1 + members 1 + displayNames 1 ≈ 4(世帯数非依存)。計測用ロガーはコミット前に削除。

- [ ] **Step 4: コミット**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/household/HouseholdDataSource.kt
git commit -m "$(cat <<'EOF'
perf(infra): HouseholdDataSource.listByResident の 1+3N を一括取得へ

世帯ごとの per-household hydrate を household_id IN (...) の一括取得に置換
(最新名 1 / 現メンバー window 1 / displayName バッチ 1)。世帯 N に対する
クエリ数を 1+3N → 定数へ削減。HouseholdDataSourceTest で挙動非退行を確認。
EOF
)"
```

---

## Task 6: Product / Invitation round-trip(3-1 の残り)

性能課題のない 2 系統の Hydration を固定する。Product は最新 revision window(unit/minimum/status)・wanted・archived 分岐、Invitation は validity window(issue→有効 / revoke→無効)。

**Files:**
- Test: `backend/core/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/product/ProductDataSourceTest.kt`
- Test: `backend/core/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/invitation/InvitationDataSourceTest.kt`

- [ ] **Step 1: ProductDataSourceTest を書く**

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.product

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.shopping.Wanted
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.infrastructure.datasource.household.HouseholdRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.resident.ResidentRegisterDataSource
import net.brightroom.mindstock.testfixtures.TestDatabase

@Tags("integration")
class ProductDataSourceTest :
    FunSpec({
        val db = TestDatabase.database
        val residents = ResidentRegisterDataSource(db)
        val households = HouseholdRegisterDataSource(db)
        val writer = ProductRegisterDataSource(db)
        val reader = ProductDataSource(db)

        beforeTest { TestDatabase.clean() }

        fun household(): Household {
            val owner =
                residents.registerResident(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("p-owner")), DisplayName("ぬし"))
            val h = Household.create(HouseholdName("家"), owner)
            households.registerHousehold(h)
            return h
        }

        test("findById: custom 商品が最新 revision(unit/minimum)付きで引ける") {
            val h = household()
            val product = Product.custom(ProductName("牛乳"), Barcode.Unlinked, ProductUnit("本"), MinimumStock(2))
            writer.registerCustom(product, h.id)

            val loaded = reader.findById(product.id)
            loaded.name() shouldBe "牛乳"
            loaded.setting.unit() shouldBe "本"
            loaded.setting.minimumStock() shouldBe 2
        }

        test("listByHousehold: 採用中のみ、setWanted した商品は listWanted に出る") {
            val h = household()
            val a = Product.custom(ProductName("卵"), Barcode.Unlinked, ProductUnit("個"), MinimumStock(1))
            val b = Product.custom(ProductName("パン"), Barcode.Unlinked, ProductUnit("斤"), MinimumStock(1))
            writer.registerCustom(a, h.id)
            writer.registerCustom(b, h.id)
            writer.setWanted(a.id, Wanted(true))

            reader.listByHousehold(h.id).list.size shouldBe 2
            reader.listWanted(h.id).list.map { it.id }.toSet() shouldBe setOf(a.id)
        }
    })
```

> 検証メモ: `Product.setting.unit()` / `minimumStock()` / `Wanted(Boolean)`(`domain.model.inventory.shopping.Wanted`、フェーズ 1-5 で導入)の実 API を Read で確認。archived の検証は `ProductRegisterDataSource.appendRevision` に `status=アーカイブ済` の Product を渡す経路が要るため、ドメインの archive メソッドが Product を返す形を確認できたら追加(難しければ本タスクでは listByHousehold/listWanted までで足りる)。

- [ ] **Step 2: InvitationDataSourceTest を書く**

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.invitation

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.invitation.InvitationValidity
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.infrastructure.datasource.household.HouseholdRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.resident.ResidentRegisterDataSource
import net.brightroom.mindstock.testfixtures.TestDatabase

@Tags("integration")
class InvitationDataSourceTest :
    FunSpec({
        val db = TestDatabase.database
        val residents = ResidentRegisterDataSource(db)
        val households = HouseholdRegisterDataSource(db)
        val writer = InvitationRegisterDataSource(db)
        val reader = InvitationDataSource(db)

        beforeTest { TestDatabase.clean() }

        fun household(): Household {
            val owner =
                residents.registerResident(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("i-owner")), DisplayName("ぬし"))
            val h = Household.create(HouseholdName("家"), owner)
            households.registerHousehold(h)
            return h
        }

        test("issue した招待は有効で引ける") {
            val h = household()
            val issued = writer.issue(Invitation.issue(h.id, HouseholdMemberRole.メンバー))

            val loaded = reader.findByCode(issued.code)
            loaded.householdId shouldBe h.id
            loaded.grantedRole shouldBe HouseholdMemberRole.メンバー
            loaded.validity shouldBe InvitationValidity.有効
        }

        test("revoke すると最新 validity が無効になる") {
            val h = household()
            val issued = writer.issue(Invitation.issue(h.id, HouseholdMemberRole.メンバー))
            writer.revoke(issued.code)

            reader.findByCode(issued.code).validity shouldBe InvitationValidity.無効
        }
    })
```

> 検証メモ: `Invitation.issue(householdId, role)`(`household/invitation/Invitation.kt:19`)、`Invitation.code`/`.householdId`/`.grantedRole`/`.validity` の実プロパティ名を Read で確認。`InvitationValidity` の enum 値(`有効`/`無効`)。

- [ ] **Step 3: 実行して green を確認**

Run: `./gradlew :backend:core:integrationTest --tests "*ProductDataSourceTest" --tests "*InvitationDataSourceTest"`
Expected: PASS。

- [ ] **Step 4: コミット**

```bash
git add backend/core/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/product/ProductDataSourceTest.kt \
  backend/core/src/test/kotlin/net/brightroom/mindstock/infrastructure/datasource/invitation/InvitationDataSourceTest.kt
git commit -m "test(infra): Product / Invitation DataSource の Hydration round-trip を追加"
```

---

## Task 7: HouseholdRegisterDataSource の membership insert 重複を helper 化(3-4)

`joinMember` / `changeMemberRole` / `removeMember` の 3 メソッドで重複する `HouseholdMembershipEventsTable.insert` を private helper に集約する。`registerHousehold` 内のループ insert も同 helper に寄せる。挙動は不変(Task 4 の HouseholdDataSourceTest が安全網)。

**Files:**
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/household/HouseholdRegisterDataSource.kt`

- [ ] **Step 1: private helper を追加**

クラス末尾に追加:

```kotlin
    /** household_membership_events への 1 行 insert(所属/除外イベントの共通形)。tx 内で呼ぶ前提。 */
    private fun insertMembershipEvent(
        householdId: HouseholdId,
        residentId: ResidentId,
        role: HouseholdMemberRole,
        status: MembershipStatus,
        recordedAt: LocalDateTime,
    ) {
        HouseholdMembershipEventsTable.insert {
            it[HouseholdMembershipEventsTable.householdId] = householdId()
            it[HouseholdMembershipEventsTable.residentId] = residentId()
            it[HouseholdMembershipEventsTable.role] = role
            it[HouseholdMembershipEventsTable.status] = status
            it[HouseholdMembershipEventsTable.recordedAt] = recordedAt
        }
    }
```

> `recordedAt` 列への代入は **必ずカラムオブジェクトで明示修飾**する(`it[HouseholdMembershipEventsTable.recordedAt]`)。`it[recordedAt]` だと引数 `recordedAt: LocalDateTime` とカラムが同名でシャドーイングし Exposed DSL が壊れる。型は `import kotlinx.datetime.LocalDateTime` を足して `recordedAt: LocalDateTime`(完全修飾でなく短縮)にする。tx 内で `Created.now()()`(= `LocalDateTime`)を渡す。

- [ ] **Step 2: 3 メソッド + registerHousehold ループを helper 呼び出しに置換**

- `joinMember`: `insertMembershipEvent(householdId, resident.id, role, MembershipStatus.所属, Created.now()())`(tx 内)
- `changeMemberRole`: `insertMembershipEvent(householdId, residentId, role, MembershipStatus.所属, Created.now()())`(tx 内)
- `removeMember`: `insertMembershipEvent(householdId, residentId, HouseholdMemberRole.閲覧者, MembershipStatus.除外, Created.now()())`(tx 内。tombstone の role コメントは helper 呼び出し直前に残す)
- `registerHousehold` の `household.members.list.forEach { m -> ... }`: `insertMembershipEvent(household.id, m.resident.id, m.role, MembershipStatus.所属, createdTime())`

各メソッドで `transaction(database) { ... }` の境界は維持し、helper は **必ず tx 内**で呼ぶ。

- [ ] **Step 3: 実行して green を確認**

Run: `./gradlew :backend:core:integrationTest --tests "*HouseholdDataSourceTest"`
Expected: PASS(Task 4 の安全網で挙動非退行)。加えて `./gradlew :backend:core:test`(ユニット側のコンパイル確認)。

- [ ] **Step 4: コミット**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/household/HouseholdRegisterDataSource.kt
git commit -m "refactor(infra): household membership insert を insertMembershipEvent に共通化"
```

---

## Task 8: registerHousehold の Created.now() を tx 内へ統一(3-5)

`HouseholdRegisterDataSource.registerHousehold` の `Created.now()` を tx 外取得から tx 内取得へ移し、「永続化時刻は原則 tx 内で取得」をコード上で一貫させる(`issue` の retry ループは許容例外として現状維持)。

**Files:**
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/household/HouseholdRegisterDataSource.kt:24-47`

- [ ] **Step 1: `Created.now()` を transaction ブロック内へ移動**

`registerHousehold` を以下に変更(`val createdTime = Created.now()` を `transaction(database) {` の直後へ):

```kotlin
    override fun registerHousehold(household: Household) {
        transaction(database) {
            val createdTime = Created.now()
            HouseholdsTable.insert {
                it[id] = household.id()
                it[createdAt] = createdTime()
            }
            HouseholdNamesTable.insert {
                it[householdId] = household.id()
                it[name] = household.profile.name()
                it[recordedAt] = createdTime()
            }
            household.members.list.forEach { m ->
                insertMembershipEvent(household.id, m.resident.id, m.role, MembershipStatus.所属, createdTime())
            }
        }
    }
```

> Task 7 を先に終えていれば members ループは既に helper 化済み。本タスクは `Created.now()` の位置だけを動かす差分になる。

- [ ] **Step 2: 実行して green を確認**

Run: `./gradlew :backend:core:integrationTest --tests "*HouseholdDataSourceTest"`
Expected: PASS。

- [ ] **Step 3: コミット**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/household/HouseholdRegisterDataSource.kt
git commit -m "refactor(infra): registerHousehold の Created.now() を tx 内取得に統一"
```

---

## フェーズ完了の検証

- [ ] **全統合テスト green**

Run: `./gradlew :backend:core:integrationTest`
Expected: 新規 5 spec(Smoke / Stock / Household / Product / Invitation)+ 既存 `ProductImageTransferTest` が全て PASS。

- [ ] **ユニットテスト・ビルド非退行**

Run: `./gradlew :backend:core:test`(integration を除外する通常テストが green)
Run: `./gradlew build`(DB なしでも通る = integrationTest を巻き込まない)

- [ ] **計測用 `addLogger` の混入がないこと**

Run: `git grep -n "StdOutSqlLogger" backend/core/src/main`
Expected: ヒット 0(計測ロガーは本番コードに残さない)。

- [ ] **クエリ数削減の記録**

`StockDataSource.listByHousehold` と `HouseholdDataSource.listByResident` の before/after クエリ数を PR 説明に記載(N+1 → 定数)。

- [ ] **finishing-a-development-branch で PR 化**

REQUIRED SUB-SKILL: superpowers:finishing-a-development-branch。`./gradlew :backend:core:test` green を確認し、`refactor/p3-infrastructure-quality` を PR 化(マスタープランのフェーズ 3 = 1 PR)。

---

## Self-Review(spec 突き合わせ)

- **3-1**(DataSource 統合テスト Stock/Household/Product/Invitation): Task 2/4/6 で全 4 系統 + Task 1 基盤 ✅
- **3-2**(StockDataSource 2N+1 解消): Task 3 ✅(Task 2 が安全網)
- **3-3**(HouseholdDataSource 1+3N 解消): Task 5 ✅(Task 4 が安全網)
- **3-4**(membership insert 重複の helper 化): Task 7 ✅
- **3-5**(Created.now() tx 内統一): Task 8 ✅(実スコープは registerHousehold の 1 箇所と確定)
- **順序厳守(テストが先)**: Task 2→3、Task 4→5 でテスト先行を強制 ✅
- **検証(テストが書き換え前後で green / クエリ数記録)**: 各リファクタ Task の Step に内包 ✅

**未確定で実装時に Read 突き合わせが要る箇所**(プランは「何を・なぜ」が安定、「どこ/正確なシグネチャ」は揮発):
- `Stock.currentQuantity()` / `NetQuantity` の正確な呼び出し(Task 2)
- `Members.roleOf` の有無(Task 4)
- `Product.setting.unit()/minimumStock()` / `Wanted` コンストラクタ(Task 6)
- `Invitation` のプロパティ名(Task 6)
- Exposed v1 の `exec(String)` / `inList` / window alias の import(Task 1/3/5)
