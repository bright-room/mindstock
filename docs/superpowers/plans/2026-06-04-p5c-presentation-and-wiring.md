# P5c presentation Controller・認可・起動配線・失効ガード Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `:backend:api` に presentation Controller・失効ガード・起動配線を実装し、application/domain 層に世帯メンバーシップ認可を追加して、認証・認可付きで起動可能なバックエンドを完成させる。

**Architecture:** 認可は domain(`Household.requireMember`)に置き、application service が household を fetch して検証する(横方向認可 IDOR を application/domain で閉じる)。Controller は service/scenario を呼んで `guarded{}` で包むだけの薄い presentation 層。`guarded{}` は JWT 期限切れ判定 + 例外→`RpcError` 翻訳のみ(DB transaction は DataSource 自前のため張らない)。起動配線は旧コミット `11c9b31` を新アーキ(DataSource が `Database` 注入)に合わせて移植する。

**Tech Stack:** Kotlin / Ktor server(`createApplicationPlugin` / Ktor DI `dependencies`)/ kotlinx-rpc 0.10.x(`@Rpc` / `registerService`)/ Exposed v1(JDBC)/ HikariCP / Flyway / auth0 java-jwt + jwks-rsa / Kotest FunSpec + mockk(backend JVM)/ kotlin.test + Kotest assertions(domain commonTest)。

**設計の出典:** `docs/superpowers/specs/2026-06-04-p5c-presentation-and-wiring-design.md`

---

## ファイル構成

### domain(KMP common)
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/exception/MembershipRequiredException.kt`
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/Household.kt`(`requireMember` 追加)
- Create: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdMembershipTest.kt`

### backend/core(application + infrastructure)
- Modify: `application/repository/product/ProductRepository.kt`(`householdOf` 追加)
- Modify: `infrastructure/datasource/product/ProductDataSource.kt`(`householdOf` 実装)
- Modify: `application/service/product/ProductService.kt`(+actor +householdRepository)
- Modify: `application/service/stock/StockService.kt`(+actor +householdRepository +productRepository)
- Modify: `application/service/product/ProductRegisterService.kt`(+actor +householdRepository)
- Modify: `application/service/stock/StockRegisterService.kt`(+actor authz +householdRepository +productRepository)
- Modify: `application/scenario/product/AdoptProductScenario.kt`(+actor)
- Modify(tests): `ProductServiceTest.kt` / `ProductRegisterServiceTest.kt` / `StockRegisterServiceTest.kt`
- Create(test): `application/service/stock/StockServiceTest.kt`

### backend/api(presentation + configuration)
- Create: `configuration/auth/SessionAccess.kt`(`sessionOf` / `requireResidentId`)
- Create: `configuration/guard/SessionGuard.kt`(`guarded`)+ test
- Create: `presentation/rpc/{catalog,resident,household,product,stock}/<Ctx>Controller.kt`(9 個)+ 各 test
- Create: `configuration/external/exposed/ExposedDataSourceProperties.kt` / `ExposedConfiguration.kt`
- Create: `configuration/migration/MigrationConfiguration.kt`
- Create: `configuration/di/DependenciesConfiguration.kt`
- Modify: `configuration/routing/RoutingConfiguration.kt`(全面差し替え)
- Modify: `backend/api/src/main/resources/application.yaml`

**ビルド確認コマンド:**
- domain: `./gradlew :domain:build`
- core: `./gradlew :backend:core:test`
- api(統合テスト除く): `./gradlew :backend:api:build -x integrationTest`

---

## Task 1: `MembershipRequiredException` + `Household.requireMember`

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/exception/MembershipRequiredException.kt`
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/Household.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdMembershipTest.kt`

domain は KMP common のため commonTest は `kotlin.test.@Test` + Kotest assertions(`frontend-kmp-test-style`)。

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.domain.model.household

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import net.brightroom.mindstock.domain.exception.MembershipRequiredException
import net.brightroom.mindstock.domain.model.household.member.HouseholdMember
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.member.Members
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import kotlin.test.Test

class HouseholdMembershipTest {
    private fun resident(name: String) = Resident(ResidentId.create(), Profile(DisplayName(name)))

    private fun household(vararg members: Resident): Household {
        val owner = members.first()
        val rest = members.drop(1).map { HouseholdMember(it, HouseholdMemberRole.世帯員) }
        return Household(
            id = HouseholdId.create(),
            profile = Profile(HouseholdName("わが家")),
            members = Members(listOf(HouseholdMember(owner, HouseholdMemberRole.世帯主)) + rest),
        )
    }

    @Test
    fun member_passes() {
        val owner = resident("おやかた")
        val member = resident("こども")
        shouldNotThrowAny { household(owner, member).requireMember(member.id) }
    }

    @Test
    fun non_member_throws() {
        val owner = resident("おやかた")
        val stranger = resident("よそもの")
        shouldThrow<MembershipRequiredException> { household(owner).requireMember(stranger.id) }
    }
}
```

> 注: `Household` の `Profile` は `household` パッケージの `Profile(HouseholdName)`、`Resident` の `Profile` は `resident.profile.Profile(DisplayName)`。import の衝突に注意(上記は household.Profile を素の `Profile`、resident.profile.Profile を完全修飾せず import 名で解決 — 衝突する場合は resident 側を `as ResidentProfile` で alias する)。

- [ ] **Step 2: テストが失敗(コンパイル不可)することを確認**

Run: `./gradlew :domain:compileTestKotlinJvm`
Expected: FAIL(`MembershipRequiredException` / `requireMember` 未定義)

- [ ] **Step 3: 例外を作る**

```kotlin
package net.brightroom.mindstock.domain.exception

/** 世帯メンバーでない resident が世帯リソースへアクセスした。認可失敗(横方向認可)。 */
class MembershipRequiredException(
    reason: String,
) : RuntimeException(reason)
```

> `OwnerRequiredException.kt` と同形であることを確認(`class XxxException(reason: String) : RuntimeException(reason)`)。

- [ ] **Step 4: `Household.requireMember` を追加**

`Household.kt` の `requireCanManage` の直後に追加:

```kotlin
    /** 世帯メンバーであることの認可(読み書き共通の横方向認可)。非メンバーなら MembershipRequiredException。 */
    fun requireMember(by: ResidentId) {
        if (!members.contains(by)) {
            throw MembershipRequiredException("not a member of household $id: $by")
        }
    }
```

`Household.kt` の import に追加: `import net.brightroom.mindstock.domain.exception.MembershipRequiredException`

- [ ] **Step 5: テストが通ることを確認**

Run: `./gradlew :domain:test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/exception/MembershipRequiredException.kt \
        domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/Household.kt \
        domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdMembershipTest.kt
git commit -m "feat(domain): Household.requireMember(横方向認可)と MembershipRequiredException を追加"
```

---

## Task 2: `ProductRepository.householdOf`(世帯解決クエリ)

**Files:**
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/product/ProductRepository.kt`
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/product/ProductDataSource.kt`

DataSource は現状の新アーキでは自動テスト無し(integrationTest harness 未整備)。本 Task はコンパイル確認のみ。認可ロジックは後続の service テストで mockk 越しに担保する。

- [ ] **Step 1: interface に追加**

`ProductRepository.kt` に追加:

```kotlin
    /** product が属する世帯 id を解決する(認可で使用)。不在は ResourceNotFoundException。 */
    fun householdOf(productId: ProductId): HouseholdId
```

- [ ] **Step 2: ProductDataSource に実装**

`ProductDataSource.kt` の `findById` の直後に追加:

```kotlin
    override fun householdOf(productId: ProductId): HouseholdId =
        transaction(database) {
            ProductsTable
                .select(ProductsTable.householdId)
                .where { ProductsTable.id eq productId() }
                .firstOrNull()
                ?.let { HouseholdId(it[ProductsTable.householdId].value) }
                ?: throw ResourceNotFoundException("product not found: $productId")
        }
```

`ProductDataSource.kt` の import は既に `ProductsTable` / `HouseholdId` / `ResourceNotFoundException` / `select` / `transaction` / `eq` を含む(追加不要)。`HouseholdId` の constructor は public(`value class HouseholdId(private val value: Uuid)` の value だけ private)。`ProductsTable.householdId` は `reference` 列のため読み出しは `.value` で `Uuid` を得る。

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :backend:core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/product/ProductRepository.kt \
        backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/product/ProductDataSource.kt
git commit -m "feat(core): ProductRepository.householdOf(productId->世帯解決)を追加"
```

---

## Task 3: `ProductService` 認可(+actor +householdRepository)

**Files:**
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/product/ProductService.kt`
- Test: `backend/core/src/test/kotlin/net/brightroom/mindstock/application/service/product/ProductServiceTest.kt`

- [ ] **Step 1: テストを認可込みに更新(失敗させる)**

`ProductServiceTest.kt` を全面置換:

```kotlin
package net.brightroom.mindstock.application.service.product

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.exception.MembershipRequiredException
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.Profile
import net.brightroom.mindstock.domain.model.household.member.HouseholdMember
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.member.Members
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.Products
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile as ResidentProfile

class ProductServiceTest :
    FunSpec({
        val stockRepository = mockk<StockRepository>()
        val productRepository = mockk<ProductRepository>()
        val householdRepository = mockk<HouseholdRepository>()
        val service = ProductService(stockRepository, productRepository, householdRepository)

        val actor = ResidentId.create()
        val member = Resident(actor, ResidentProfile(DisplayName("じぶん")))
        val householdId = HouseholdId.create()
        fun householdWith(vararg residents: Resident) =
            Household(
                householdId,
                Profile(HouseholdName("わが家")),
                Members(residents.map { HouseholdMember(it, HouseholdMemberRole.世帯主) }),
            )

        test("list はメンバーなら在庫一覧を返す") {
            every { householdRepository.findById(householdId) } returns householdWith(member)
            every { stockRepository.listByHousehold(householdId) } returns Stocks(emptyList())
            service.list(householdId, actor) shouldBe Stocks(emptyList())
        }

        test("list は非メンバーなら MembershipRequiredException") {
            every { householdRepository.findById(householdId) } returns householdWith(Resident(ResidentId.create(), ResidentProfile(DisplayName("ほかのひと"))))
            shouldThrow<MembershipRequiredException> { service.list(householdId, actor) }
        }

        test("shoppingList は手動希望フラグを Stock に突き合わせて合成する") {
            val wanted = Product.custom(ProductName("水"), Barcode.Unlinked, ProductUnit("本"), MinimumStock(5))
            val other = Product.custom(ProductName("米"), Barcode.Unlinked, ProductUnit("袋"), MinimumStock(1))
            every { householdRepository.findById(householdId) } returns householdWith(member)
            every { stockRepository.listByHousehold(householdId) } returns
                Stocks(listOf(Stock(wanted, StockMovements(emptyList())), Stock(other, StockMovements(emptyList()))))
            every { productRepository.listWanted(householdId) } returns Products(listOf(wanted))

            val list = service.shoppingList(householdId, actor)

            list.list.first { it.stock.product.id == wanted.id }.manuallyWanted shouldBe true
            list.list.first { it.stock.product.id == other.id }.manuallyWanted shouldBe false
        }
    })
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :backend:core:test --tests "*ProductServiceTest*"`
Expected: FAIL(コンストラクタ引数不一致 / `list` の actor 引数未定義)

- [ ] **Step 3: ProductService を実装**

`ProductService.kt` を置換:

```kotlin
package net.brightroom.mindstock.application.service.product

import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Products
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingEntry
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingList
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

class ProductService(
    private val stockRepository: StockRepository,
    private val productRepository: ProductRepository,
    private val householdRepository: HouseholdRepository,
) {
    /** 在庫一覧(数量+status を見せるため Stock 集合)。 */
    fun list(
        householdId: HouseholdId,
        actor: ResidentId,
    ): Stocks {
        householdRepository.findById(householdId).requireMember(actor)
        return stockRepository.listByHousehold(householdId)
    }

    fun listArchived(
        householdId: HouseholdId,
        actor: ResidentId,
    ): Products {
        householdRepository.findById(householdId).requireMember(actor)
        return productRepository.listArchivedByHousehold(householdId)
    }

    /** 買い物リスト(自動=在庫不足 + 手動希望)。Stock 集合 × 手動希望 を read-model に合成する。 */
    fun shoppingList(
        householdId: HouseholdId,
        actor: ResidentId,
    ): ShoppingList {
        householdRepository.findById(householdId).requireMember(actor)
        val stocks = stockRepository.listByHousehold(householdId)
        val wantedIds =
            productRepository
                .listWanted(householdId)
                .list
                .map { it.id }
                .toSet()
        return ShoppingList(stocks.list.map { ShoppingEntry(it, it.product.id in wantedIds) })
    }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :backend:core:test --tests "*ProductServiceTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/product/ProductService.kt \
        backend/core/src/test/kotlin/net/brightroom/mindstock/application/service/product/ProductServiceTest.kt
git commit -m "feat(core): ProductService に世帯メンバー認可(actor)を追加"
```

---

## Task 4: `StockService` 認可(+actor +householdRepository +productRepository)

**Files:**
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/stock/StockService.kt`
- Test(新規): `backend/core/src/test/kotlin/net/brightroom/mindstock/application/service/stock/StockServiceTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.application.service.stock

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.exception.MembershipRequiredException
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.Profile
import net.brightroom.mindstock.domain.model.household.member.HouseholdMember
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.member.Members
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile as ResidentProfile

class StockServiceTest :
    FunSpec({
        val stockRepository = mockk<StockRepository>()
        val productRepository = mockk<ProductRepository>()
        val householdRepository = mockk<HouseholdRepository>()
        val service = StockService(stockRepository, productRepository, householdRepository)

        val actor = ResidentId.create()
        val member = Resident(actor, ResidentProfile(DisplayName("じぶん")))
        val householdId = HouseholdId.create()
        val productId = ProductId.create()
        fun householdWith(vararg residents: Resident) =
            Household(householdId, Profile(HouseholdName("わが家")), Members(residents.map { HouseholdMember(it, HouseholdMemberRole.世帯主) }))

        test("activity はメンバーなら在庫一覧を返す") {
            every { householdRepository.findById(householdId) } returns householdWith(member)
            every { stockRepository.listByHousehold(householdId) } returns Stocks(emptyList())
            service.activity(householdId, actor) shouldBe Stocks(emptyList())
        }

        test("history は product の世帯を解決して非メンバーを弾く") {
            every { productRepository.householdOf(productId) } returns householdId
            every { householdRepository.findById(householdId) } returns householdWith(Resident(ResidentId.create(), ResidentProfile(DisplayName("ほか"))))
            shouldThrow<MembershipRequiredException> { service.history(productId, actor) }
        }

        test("history はメンバーなら movement 履歴を返す") {
            every { productRepository.householdOf(productId) } returns householdId
            every { householdRepository.findById(householdId) } returns householdWith(member)
            every { stockRepository.historyOf(productId) } returns StockMovements(emptyList())
            service.history(productId, actor) shouldBe StockMovements(emptyList())
        }
    })
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :backend:core:test --tests "*StockServiceTest*"`
Expected: FAIL(コンストラクタ / actor 引数未定義)

- [ ] **Step 3: StockService を実装**

`StockService.kt` を置換:

```kotlin
package net.brightroom.mindstock.application.service.stock

import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

class StockService(
    private val stockRepository: StockRepository,
    private val productRepository: ProductRepository,
    private val householdRepository: HouseholdRepository,
) {
    fun history(
        productId: ProductId,
        actor: ResidentId,
    ): StockMovements {
        val householdId = productRepository.householdOf(productId)
        householdRepository.findById(householdId).requireMember(actor)
        return stockRepository.historyOf(productId)
    }

    /** 世帯全体の活動履歴。Controller(P5c)が ActivityFeed に flatten する。 */
    fun activity(
        householdId: HouseholdId,
        actor: ResidentId,
    ): Stocks {
        householdRepository.findById(householdId).requireMember(actor)
        return stockRepository.listByHousehold(householdId)
    }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :backend:core:test --tests "*StockServiceTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/stock/StockService.kt \
        backend/core/src/test/kotlin/net/brightroom/mindstock/application/service/stock/StockServiceTest.kt
git commit -m "feat(core): StockService に世帯メンバー認可(actor)を追加"
```

---

## Task 5: `ProductRegisterService` 認可(+actor +householdRepository)

**Files:**
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/product/ProductRegisterService.kt`
- Test: `backend/core/src/test/kotlin/net/brightroom/mindstock/application/service/product/ProductRegisterServiceTest.kt`

`adopt` / `addCustom` は引数 `householdId` 直、その他(`changeUnit` / `changeMinimum` / `changeImage` / `archive` / `unarchive` / `setWanted`)は `productRepository.householdOf(productId)` 経由で世帯を解決して `requireMember(actor)`。

- [ ] **Step 1: 既存テストを認可込みに更新(失敗させる)**

`ProductRegisterServiceTest.kt` を読み、`ProductRegisterService(...)` の構築に `householdRepository` を追加し、各テストの呼び出しに `actor` を渡すよう更新する。最低限、以下を満たすこと(既存テストの意図は保持):
- `val householdRepository = mockk<HouseholdRepository>()` を宣言し `ProductRegisterService(productRepository, productRegisterRepository, stockRepository, householdRepository)` で構築
- `householdId` 系: `every { householdRepository.findById(householdId) } returns <メンバー含む Household>`
- `productId` 系: `every { productRepository.householdOf(productId) } returns householdId` を追加
- 新規テストを 1 本追加:

```kotlin
        test("changeUnit は product の世帯メンバーでなければ MembershipRequiredException") {
            every { productRepository.householdOf(product.id) } returns householdId
            every { householdRepository.findById(householdId) } returns
                Household(householdId, Profile(HouseholdName("わが家")), Members(listOf(HouseholdMember(Resident(ResidentId.create(), ResidentProfile(DisplayName("ほか"))), HouseholdMemberRole.世帯主))))
            shouldThrow<MembershipRequiredException> { service.changeUnit(product.id, ProductUnit("缶"), actor) }
        }
```

(import: `Household` / `HouseholdName` / `Profile`(household)/ `HouseholdMember` / `HouseholdMemberRole` / `Members` / `MembershipRequiredException` / `ResidentProfile` alias / `shouldThrow`)

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :backend:core:test --tests "*ProductRegisterServiceTest*"`
Expected: FAIL(コンストラクタ / actor 引数)

- [ ] **Step 3: ProductRegisterService を実装**

コンストラクタに `householdRepository: HouseholdRepository` を追加し、private 認可ヘルパーと各メソッドに `actor` を追加。完全な置換版:

```kotlin
package net.brightroom.mindstock.application.service.product

import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.product.ProductRegisterRepository
import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.exception.DuplicateJanException
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.image.ProductImage
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

class ProductRegisterService(
    private val productRepository: ProductRepository,
    private val productRegisterRepository: ProductRegisterRepository,
    private val stockRepository: StockRepository,
    private val householdRepository: HouseholdRepository,
) {
    private fun authorize(
        householdId: HouseholdId,
        actor: ResidentId,
    ) = householdRepository.findById(householdId).requireMember(actor)

    private fun authorizeProduct(
        productId: ProductId,
        actor: ResidentId,
    ) = authorize(productRepository.householdOf(productId), actor)

    fun adopt(
        catalogItem: CatalogItem,
        householdId: HouseholdId,
        unit: ProductUnit,
        minimumStock: MinimumStock,
        actor: ResidentId,
    ): Product {
        authorize(householdId, actor)
        if (productRepository.existsByJan(householdId, catalogItem.jan)) {
            throw DuplicateJanException("already adopted: ${catalogItem.jan}")
        }
        val product = Product.adopt(catalogItem, unit, minimumStock)
        productRegisterRepository.registerAdopted(product, householdId, catalogItem.id)
        return product
    }

    fun addCustom(
        householdId: HouseholdId,
        name: ProductName,
        barcode: Barcode,
        unit: ProductUnit,
        minimumStock: MinimumStock,
        actor: ResidentId,
    ): Product {
        authorize(householdId, actor)
        (barcode as? Barcode.Linked)?.let {
            if (productRepository.existsByJan(householdId, it.jan)) {
                throw DuplicateJanException("already adopted: ${it.jan}")
            }
        }
        val product = Product.custom(name, barcode, unit, minimumStock)
        productRegisterRepository.registerCustom(product, householdId)
        return product
    }

    fun changeUnit(
        productId: ProductId,
        unit: ProductUnit,
        actor: ResidentId,
    ) {
        authorizeProduct(productId, actor)
        val product = productRepository.findById(productId)
        productRegisterRepository.appendRevision(product.changeUnit(unit))
    }

    fun changeMinimum(
        productId: ProductId,
        minimumStock: MinimumStock,
        actor: ResidentId,
    ) {
        authorizeProduct(productId, actor)
        val product = productRepository.findById(productId)
        productRegisterRepository.appendRevision(product.changeMinimum(minimumStock))
    }

    fun changeImage(
        productId: ProductId,
        image: ProductImage,
        actor: ResidentId,
    ) {
        authorizeProduct(productId, actor)
        val product = productRepository.findById(productId)
        productRegisterRepository.appendRevision(product.changeImage(image))
    }

    fun archive(
        productId: ProductId,
        actor: ResidentId,
    ) {
        authorizeProduct(productId, actor)
        val product = productRepository.findById(productId)
        productRegisterRepository.appendRevision(product.archive())
    }

    fun unarchive(
        productId: ProductId,
        actor: ResidentId,
    ) {
        authorizeProduct(productId, actor)
        val product = productRepository.findById(productId)
        productRegisterRepository.appendRevision(product.unarchive())
    }

    fun setWanted(
        productId: ProductId,
        wanted: Boolean,
        actor: ResidentId,
    ) {
        authorizeProduct(productId, actor)
        productRegisterRepository.recordWanted(productId, wanted)
    }
}
```

> **重要:** 上記の `changeImage` / `archive` / `unarchive` / `setWanted` 本体(`product.changeImage` / `product.archive` / `product.unarchive` / `recordWanted` 等)は **既存 `ProductRegisterService.kt` の現行実装をそのまま使うこと**。現行ファイルを開いて各メソッド本体を確認し、`authorize`/`authorizeProduct` 呼び出しと `actor` 引数だけを足す(本体ロジックは変えない)。`setWanted` の repository メソッド名(`recordWanted` 等)は現行実装に合わせる。

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :backend:core:test --tests "*ProductRegisterServiceTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/product/ProductRegisterService.kt \
        backend/core/src/test/kotlin/net/brightroom/mindstock/application/service/product/ProductRegisterServiceTest.kt
git commit -m "feat(core): ProductRegisterService に世帯メンバー認可(actor)を追加"
```

---

## Task 6: `StockRegisterService` 認可(+householdRepository +productRepository)

**Files:**
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/stock/StockRegisterService.kt`
- Test: `backend/core/src/test/kotlin/net/brightroom/mindstock/application/service/stock/StockRegisterServiceTest.kt`

既に `actor` は受けている。世帯メンバー認可(`householdOf`→`requireMember`)を先頭に追加する。

- [ ] **Step 1: テストを認可込みに更新(失敗させる)**

`StockRegisterServiceTest.kt` を更新:
- `val householdRepository = mockk<HouseholdRepository>()` と `val productRepository = mockk<ProductRepository>()` を宣言し、`StockRegisterService(residentRepository, stockRepository, stockRegisterRepository, householdRepository, productRepository)` で構築
- 各テストに認可スタブを追加:
  - replenish/consume: `every { productRepository.householdOf(product.id) } returns householdId` + `every { householdRepository.findById(householdId) } returns <member 含む Household>`
  - correct: `every { productRepository.householdOf(product.id) } returns householdId`(correct は findByMovement で stock を取得 → stock.product.id で householdOf)+ `every { householdRepository.findById(householdId) } returns <member>`
- 新規テスト 1 本:

```kotlin
        test("replenish は product の世帯メンバーでなければ MembershipRequiredException") {
            every { productRepository.householdOf(product.id) } returns householdId
            every { householdRepository.findById(householdId) } returns
                Household(householdId, Profile(HouseholdName("わが家")), Members(listOf(HouseholdMember(Resident(ResidentId.create(), ResidentProfile(DisplayName("ほか"))), HouseholdMemberRole.世帯主))))
            shouldThrow<MembershipRequiredException> { service.replenish(product.id, Quantity(1), Note(""), actor.id) }
        }
```

(`householdId` を spec の先頭で `val householdId = HouseholdId.create()` として宣言)

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :backend:core:test --tests "*StockRegisterServiceTest*"`
Expected: FAIL(コンストラクタ引数)

- [ ] **Step 3: StockRegisterService を実装**

```kotlin
package net.brightroom.mindstock.application.service.stock

import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.resident.ResidentRepository
import net.brightroom.mindstock.application.repository.stock.StockRegisterRepository
import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Reason
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

class StockRegisterService(
    private val residentRepository: ResidentRepository,
    private val stockRepository: StockRepository,
    private val stockRegisterRepository: StockRegisterRepository,
    private val householdRepository: HouseholdRepository,
    private val productRepository: ProductRepository,
) {
    private fun authorizeProduct(
        productId: ProductId,
        actor: ResidentId,
    ) = householdRepository.findById(productRepository.householdOf(productId)).requireMember(actor)

    fun replenish(
        productId: ProductId,
        quantity: Quantity,
        note: Note,
        actor: ResidentId,
    ) {
        authorizeProduct(productId, actor)
        val resident = residentRepository.findById(actor)
        val stock = stockRepository.findByProduct(productId)
        val replenished = stock.replenish(quantity, OccurredAt.now(), resident, note)
        stockRegisterRepository.appendMovement(productId, replenished.latestMovement())
    }

    fun consume(
        productId: ProductId,
        quantity: Quantity,
        note: Note,
        actor: ResidentId,
    ) {
        authorizeProduct(productId, actor)
        val resident = residentRepository.findById(actor)
        val stock = stockRepository.findByProduct(productId)
        val consumed = stock.consume(quantity, OccurredAt.now(), resident, note)
        stockRegisterRepository.appendMovement(productId, consumed.latestMovement())
    }

    /** RPC correct は productId を受けない。MovementId から Stock を丸ごと load して訂正する。 */
    fun correct(
        target: MovementId,
        correctedQuantity: Quantity,
        reason: Reason,
        actor: ResidentId,
    ) {
        val stock = stockRepository.findByMovement(target)
        householdRepository.findById(productRepository.householdOf(stock.product.id)).requireMember(actor)
        val resident = residentRepository.findById(actor)
        val corrected = stock.correct(target, correctedQuantity, reason, resident, OccurredAt.now())
        stockRegisterRepository.appendMovement(stock.product.id, corrected.latestMovement())
    }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :backend:core:test --tests "*StockRegisterServiceTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/stock/StockRegisterService.kt \
        backend/core/src/test/kotlin/net/brightroom/mindstock/application/service/stock/StockRegisterServiceTest.kt
git commit -m "feat(core): StockRegisterService に世帯メンバー認可を追加"
```

---

## Task 7: `AdoptProductScenario`(+actor)

**Files:**
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/scenario/product/AdoptProductScenario.kt`
- Test: `backend/core/src/test/kotlin/net/brightroom/mindstock/application/scenario/product/AdoptProductScenarioTest.kt`(存在すれば更新。無ければ作成不要 — 下記注を参照)

`productRegisterService.adopt` が `actor` を要求するようになったため伝播する。

- [ ] **Step 1: 現行 Scenario を確認し actor を追加**

`AdoptProductScenario.kt` を置換:

```kotlin
package net.brightroom.mindstock.application.scenario.product

import net.brightroom.mindstock.application.service.catalog.CatalogService
import net.brightroom.mindstock.application.service.product.ProductRegisterService
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

class AdoptProductScenario(
    private val catalogService: CatalogService,
    private val productRegisterService: ProductRegisterService,
) {
    fun run(
        householdId: HouseholdId,
        catalogItemId: CatalogItemId,
        unit: ProductUnit,
        minimumStock: MinimumStock,
        actor: ResidentId,
    ): Product {
        val catalogItem = catalogService.findById(catalogItemId)
        return productRegisterService.adopt(catalogItem, householdId, unit, minimumStock, actor)
    }
}
```

> **注:** 上記 `run` 本体(`catalogService.findById` → `productRegisterService.adopt`)は現行実装を踏襲し、引数末尾に `actor` を足して `adopt(..., actor)` に渡すだけ。現行ファイルの import/本体を確認して差分を最小化する。

- [ ] **Step 2: 既存テストがあれば更新**

`find backend/core/src/test -name "AdoptProductScenarioTest.kt"` で存在確認。あれば `run(...)` 呼び出しに `actor = ResidentId.create()` を追加し、`adopt` mock の引数末尾に actor を含める(`every { productRegisterService.adopt(any(), any(), any(), any(), any()) } returns ...`)。無ければ本 Step はスキップ。

- [ ] **Step 3: core 全テストを実行**

Run: `./gradlew :backend:core:test`
Expected: PASS(Task 3-7 の全変更が green)

- [ ] **Step 4: Commit**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/application/scenario/product/AdoptProductScenario.kt
git add backend/core/src/test/kotlin/net/brightroom/mindstock/application/scenario/product/AdoptProductScenarioTest.kt 2>/dev/null || true
git commit -m "feat(core): AdoptProductScenario に actor を伝播"
```

---

## Task 8: session アクセスヘルパー(`sessionOf` / `requireResidentId`)

**Files:**
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/SessionAccess.kt`

routing 内で `call` から session を取り出す `sessionOf` と、`Registered` から `residentId` を取り出す `requireResidentId` を提供する。コンパイルのみ(利用は後続 Controller/routing)。

- [ ] **Step 1: 実装を書く**

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package net.brightroom.mindstock.configuration.auth

import io.ktor.server.application.ApplicationCall
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

/** WS upgrade 時に MindstockAuthPlugin が格納した session を取り出す。 */
fun sessionOf(call: ApplicationCall): MindstockSession = call.attributes[MindstockSessionKey]

/**
 * Registered のときだけ residentId を返す。RequireRegisteredUserPlugin 配下では常に Registered のため、
 * Unregistered での呼び出しは到達しない不変条件違反(IllegalStateException → guarded で Internal)。
 */
fun MindstockSession.requireResidentId(): ResidentId =
    when (this) {
        is MindstockSession.Registered -> residentId
        is MindstockSession.Unregistered -> error("registered session required")
    }
```

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :backend:api:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/SessionAccess.kt
git commit -m "feat(api): session アクセスヘルパー(sessionOf/requireResidentId)を追加"
```

---

## Task 9: 失効ガード `guarded{}`

**Files:**
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/guard/SessionGuard.kt`
- Test: `backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/guard/SessionGuardTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package net.brightroom.mindstock.configuration.guard

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.exception.DuplicateJanException
import net.brightroom.mindstock.domain.exception.MembershipRequiredException
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

class SessionGuardTest :
    FunSpec({
        val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub"))
        fun active() = MindstockSession.Registered(identity, ResidentId.create(), Clock.System.now().plus(1.hours), Uuid.random())
        fun expired() = MindstockSession.Registered(identity, ResidentId.create(), Clock.System.now().minus(1.hours), Uuid.random())

        test("期限切れ session は Unauthorized で短絡(block は実行されない)") {
            var ran = false
            val result = guarded<Unit>(expired()) { ran = true; RpcResult.Ok(Unit) }
            ran shouldBe false
            val err = result.shouldBeInstanceOf<RpcResult.Err<RpcError>>()
            err.error.shouldBeInstanceOf<RpcError.Unauthorized>()
        }

        test("正常系は block の結果をそのまま返す") {
            guarded(active()) { RpcResult.Ok(42) } shouldBe RpcResult.Ok(42)
        }

        test("IllegalArgumentException は BadRequest") {
            val r = guarded<Unit>(active()) { throw IllegalArgumentException("bad") }
            (r as RpcResult.Err).error.shouldBeInstanceOf<RpcError.BadRequest>()
        }

        test("ResourceNotFoundException は NotFound") {
            val r = guarded<Unit>(active()) { throw ResourceNotFoundException("x not found") }
            (r as RpcResult.Err).error.shouldBeInstanceOf<RpcError.NotFound>()
        }

        test("MembershipRequiredException は Unauthorized") {
            val r = guarded<Unit>(active()) { throw MembershipRequiredException("not member") }
            (r as RpcResult.Err).error.shouldBeInstanceOf<RpcError.Unauthorized>()
        }

        test("DuplicateJanException は Conflict") {
            val r = guarded<Unit>(active()) { throw DuplicateJanException("dup") }
            (r as RpcResult.Err).error.shouldBeInstanceOf<RpcError.Conflict>()
        }

        test("想定外例外は Internal") {
            val r = guarded<Unit>(active()) { throw RuntimeException("boom") }
            (r as RpcResult.Err).error.shouldBeInstanceOf<RpcError.Internal>()
        }
    })
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :backend:api:test --tests "*SessionGuardTest*"`
Expected: FAIL(`guarded` 未定義)

- [ ] **Step 3: 実装を書く**

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package net.brightroom.mindstock.configuration.guard

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.supervisorScope
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.exception.CannotArchiveWithStockException
import net.brightroom.mindstock.domain.exception.DuplicateJanException
import net.brightroom.mindstock.domain.exception.InsufficientStockException
import net.brightroom.mindstock.domain.exception.InvitationInvalidException
import net.brightroom.mindstock.domain.exception.LastOwnerException
import net.brightroom.mindstock.domain.exception.MembershipRequiredException
import net.brightroom.mindstock.domain.exception.OwnerRequiredException
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.time.Clock

private val logger = KotlinLogging.logger {}

/**
 * RPC message 単位の失効ガード + 例外→RpcError 翻訳。
 *
 * - 接続時に保存した session.exp を現在時刻と比較し、期限切れなら Unauthorized で短絡(L2 失効ガード)。
 *   WS は長時間張りっぱなしのため、upgrade 時の 1 回検証だけでは期限切れを取りこぼす。
 * - supervisorScope で block を実行し、kRPC サーバスコープへの例外 leak を防ぐ。
 * - block 内のドメイン例外を RpcError に翻訳する(DB transaction は張らない。境界は DataSource 自前)。
 *
 * IdP 側の即時失効(revocation list)は対象外。守るのは JWT の有効期限切れのみ。
 */
suspend fun <T : Any> guarded(
    session: MindstockSession,
    block: suspend () -> RpcResult<T, RpcError>,
): RpcResult<T, RpcError> {
    if (Clock.System.now() > session.exp) {
        return RpcResult.Err(RpcError.Unauthorized(reason = "token expired"))
    }
    return try {
        supervisorScope { block() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IllegalArgumentException) {
        RpcResult.Err(RpcError.BadRequest(field = "request", reason = e.message ?: "invalid request"))
    } catch (e: ResourceNotFoundException) {
        RpcResult.Err(RpcError.NotFound(message = e.message ?: "not found"))
    } catch (e: OwnerRequiredException) {
        RpcResult.Err(RpcError.Unauthorized(reason = e.message ?: "owner required"))
    } catch (e: MembershipRequiredException) {
        RpcResult.Err(RpcError.Unauthorized(reason = e.message ?: "membership required"))
    } catch (e: LastOwnerException) {
        RpcResult.Err(RpcError.Conflict(reason = e.message ?: "last owner"))
    } catch (e: DuplicateJanException) {
        RpcResult.Err(RpcError.Conflict(reason = e.message ?: "duplicate"))
    } catch (e: CannotArchiveWithStockException) {
        RpcResult.Err(RpcError.Conflict(reason = e.message ?: "cannot archive with stock"))
    } catch (e: InsufficientStockException) {
        RpcResult.Err(RpcError.Conflict(reason = e.message ?: "insufficient stock"))
    } catch (e: InvitationInvalidException) {
        RpcResult.Err(RpcError.Conflict(reason = e.message ?: "invitation invalid"))
    } catch (e: Throwable) {
        logger.error(e) { "unhandled exception during RPC call_id=${session.callId}" }
        RpcResult.Err(RpcError.Internal(reason = "unexpected server error"))
    }
}
```

> `catch` 順序が重要: `IllegalArgumentException` は他のドメイン例外(`RuntimeException` 直系)より先でも後でもよいが、`Throwable` は必ず最後。各ドメイン例外は互いに継承関係が無いので順序自由。

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :backend:api:test --tests "*SessionGuardTest*"`
Expected: PASS(7 tests)

- [ ] **Step 5: Commit**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/guard/SessionGuard.kt \
        backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/guard/SessionGuardTest.kt
git commit -m "feat(api): 失効ガード guarded{}(期限切れ判定+例外->RpcError 翻訳)を追加"
```

---

## Controller 共通: テスト用 session ヘルパー

以降の Controller テストは `MindstockSession.Registered` を構築する。各テストファイル先頭で以下を用意する(import 込み):

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)
// ...
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid
// spec body 内:
val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub"))
fun registeredSession(residentId: ResidentId) =
    MindstockSession.Registered(identity, residentId, Clock.System.now().plus(1.hours), Uuid.random())
```

---

## Task 10: `CatalogController`

**Files:**
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/catalog/CatalogController.kt`
- Test: `backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/rpc/catalog/CatalogControllerTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package net.brightroom.mindstock.presentation.rpc.catalog

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import net.brightroom.mindstock.application.service.catalog.CatalogService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItems
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

class CatalogControllerTest :
    FunSpec({
        val catalogService = mockk<CatalogService>()
        val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub"))
        val session: MindstockSession = MindstockSession.Registered(identity, ResidentId.create(), Clock.System.now().plus(1.hours), Uuid.random())
        val controller = CatalogController(catalogService, session)

        test("search は service の結果を Ok で返す") {
            val empty = CatalogItems(emptyList())
            every { catalogService.search(CatalogItemName("米"), 10) } returns empty
            controller.search(CatalogItemName("米"), 10) shouldBe RpcResult.Ok(empty)
        }
    })
```

> `CatalogItems` / `CatalogItemName` の正確な package は `rpc/catalog/CatalogRpcService.kt` の import を参照して合わせる(`domain.model.catalog.item.*` の想定)。`lookupByJan` 戻り値 `CatalogItem` / `Jan` も同様。

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :backend:api:test --tests "*CatalogControllerTest*"`
Expected: FAIL(`CatalogController` 未定義)

- [ ] **Step 3: 実装を書く**

```kotlin
package net.brightroom.mindstock.presentation.rpc.catalog

import net.brightroom.mindstock.application.service.catalog.CatalogService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.guard.guarded
import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItems
import net.brightroom.mindstock.rpc.catalog.CatalogRpcService
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

class CatalogController(
    private val catalogService: CatalogService,
    private val session: MindstockSession,
) : CatalogRpcService {
    override suspend fun search(
        name: CatalogItemName,
        limit: Int,
    ): RpcResult<CatalogItems, RpcError> = guarded(session) { RpcResult.Ok(catalogService.search(name, limit)) }

    override suspend fun lookupByJan(jan: Jan): RpcResult<CatalogItem, RpcError> =
        guarded(session) { RpcResult.Ok(catalogService.lookupByJan(jan)) }
}
```

> import の `CatalogItemName` / `CatalogItems` / `CatalogItem` / `Jan` は `CatalogRpcService.kt` と `CatalogService.kt` の import に厳密に合わせる。

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :backend:api:test --tests "*CatalogControllerTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/catalog/CatalogController.kt \
        backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/rpc/catalog/CatalogControllerTest.kt
git commit -m "feat(api): CatalogController を追加"
```

---

## Task 11: `ResidentController`

**Files:**
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/resident/ResidentController.kt`
- Test: `backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/rpc/resident/ResidentControllerTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package net.brightroom.mindstock.presentation.rpc.resident

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import net.brightroom.mindstock.application.service.resident.ResidentService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

class ResidentControllerTest :
    FunSpec({
        val residentService = mockk<ResidentService>()
        val residentId = ResidentId.create()
        val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub"))
        val session: MindstockSession = MindstockSession.Registered(identity, residentId, Clock.System.now().plus(1.hours), Uuid.random())
        val controller = ResidentController(residentService, session)

        test("me は session の residentId で service を呼ぶ") {
            val me = Resident(residentId, Profile(DisplayName("じぶん")))
            every { residentService.me(residentId) } returns me
            controller.me() shouldBe RpcResult.Ok(me)
        }
    })
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :backend:api:test --tests "*ResidentControllerTest*"`
Expected: FAIL

- [ ] **Step 3: 実装を書く**

```kotlin
package net.brightroom.mindstock.presentation.rpc.resident

import net.brightroom.mindstock.application.service.resident.ResidentService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.auth.requireResidentId
import net.brightroom.mindstock.configuration.guard.guarded
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.rpc.resident.ResidentRpcService
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

class ResidentController(
    private val residentService: ResidentService,
    private val session: MindstockSession,
) : ResidentRpcService {
    override suspend fun me(): RpcResult<Resident, RpcError> =
        guarded(session) { RpcResult.Ok(residentService.me(session.requireResidentId())) }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :backend:api:test --tests "*ResidentControllerTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/resident/ResidentController.kt \
        backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/rpc/resident/ResidentControllerTest.kt
git commit -m "feat(api): ResidentController(me)を追加"
```

---

## Task 12: `ResidentRegisterController`(public・状態分岐)

**Files:**
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/resident/ResidentRegisterController.kt`
- Test: `backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/rpc/resident/ResidentRegisterControllerTest.kt`

`registerDisplayName` は `Unregistered` 必須、`rename` は `Registered` 必須。`when(session)` で網羅分岐。

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package net.brightroom.mindstock.presentation.rpc.resident

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.brightroom.mindstock.application.service.resident.ResidentRegisterService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

class ResidentRegisterControllerTest :
    FunSpec({
        val service = mockk<ResidentRegisterService>(relaxed = true)
        val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub"))
        val exp = Clock.System.now().plus(1.hours)
        fun unregistered() = MindstockSession.Unregistered(identity, exp, Uuid.random())
        fun registered(id: ResidentId) = MindstockSession.Registered(identity, id, exp, Uuid.random())

        test("registerDisplayName は Unregistered なら register を呼んで Ok") {
            val controller = ResidentRegisterController(service, unregistered())
            val created = Resident(ResidentId.create(), Profile(DisplayName("しんき")))
            every { service.register(identity, DisplayName("しんき")) } returns created
            controller.registerDisplayName(DisplayName("しんき")) shouldBe RpcResult.Ok(created)
        }

        test("registerDisplayName は Registered なら Conflict(register を呼ばない)") {
            val controller = ResidentRegisterController(service, registered(ResidentId.create()))
            val r = controller.registerDisplayName(DisplayName("again"))
            (r as RpcResult.Err).error.shouldBeInstanceOf<RpcError.Conflict>()
            verify(exactly = 0) { service.register(any(), any()) }
        }

        test("rename は Registered なら residentId で rename して Ok") {
            val id = ResidentId.create()
            val controller = ResidentRegisterController(service, registered(id))
            controller.rename(DisplayName("あらた")) shouldBe RpcResult.Ok(Unit)
            verify { service.rename(id, DisplayName("あらた")) }
        }

        test("rename は Unregistered なら Unauthorized") {
            val controller = ResidentRegisterController(service, unregistered())
            val r = controller.rename(DisplayName("x"))
            (r as RpcResult.Err).error.shouldBeInstanceOf<RpcError.Unauthorized>()
        }
    })
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :backend:api:test --tests "*ResidentRegisterControllerTest*"`
Expected: FAIL

- [ ] **Step 3: 実装を書く**

```kotlin
package net.brightroom.mindstock.presentation.rpc.resident

import net.brightroom.mindstock.application.service.resident.ResidentRegisterService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.guard.guarded
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.rpc.resident.ResidentRegisterRpcService
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

/**
 * public ルート(RequireRegisteredUserPlugin 非適用)。JWT 有効なら未登録でも到達する。
 * registerDisplayName は初回登録専用、rename は登録済み専用。session 状態で分岐する。
 */
class ResidentRegisterController(
    private val residentRegisterService: ResidentRegisterService,
    private val session: MindstockSession,
) : ResidentRegisterRpcService {
    override suspend fun registerDisplayName(displayName: DisplayName): RpcResult<Resident, RpcError> =
        guarded(session) {
            when (session) {
                is MindstockSession.Registered -> RpcResult.Err(RpcError.Conflict(reason = "already registered"))
                is MindstockSession.Unregistered -> RpcResult.Ok(residentRegisterService.register(session.identity, displayName))
            }
        }

    override suspend fun rename(displayName: DisplayName): RpcResult<Unit, RpcError> =
        guarded(session) {
            when (session) {
                is MindstockSession.Registered -> {
                    residentRegisterService.rename(session.residentId, displayName)
                    RpcResult.Ok(Unit)
                }
                is MindstockSession.Unregistered -> RpcResult.Err(RpcError.Unauthorized(reason = "registration required"))
            }
        }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :backend:api:test --tests "*ResidentRegisterControllerTest*"`
Expected: PASS(4 tests)

- [ ] **Step 5: Commit**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/resident/ResidentRegisterController.kt \
        backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/rpc/resident/ResidentRegisterControllerTest.kt
git commit -m "feat(api): ResidentRegisterController(状態分岐: register/rename)を追加"
```

---

## Task 13: `HouseholdController`(list / previewInvite)

**Files:**
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/household/HouseholdController.kt`
- Test: `backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/rpc/household/HouseholdControllerTest.kt`

`previewInvite` は `InvitationService.findByCode` + `HouseholdService.findById` を合成して presentation DTO `InvitationPreview` を組み立てる(腐敗防止層 mapping)。

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package net.brightroom.mindstock.presentation.rpc.household

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.invitation.InvitationService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.Households
import net.brightroom.mindstock.domain.model.household.Profile
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.domain.model.household.invitation.InvitationValidity
import net.brightroom.mindstock.domain.model.household.member.HouseholdMember
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.member.Members
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile as ResidentProfile
import net.brightroom.mindstock.rpc.household.InvitationPreview
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

class HouseholdControllerTest :
    FunSpec({
        val householdService = mockk<HouseholdService>()
        val invitationService = mockk<InvitationService>()
        val residentId = ResidentId.create()
        val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub"))
        val session: MindstockSession = MindstockSession.Registered(identity, residentId, Clock.System.now().plus(1.hours), Uuid.random())
        val controller = HouseholdController(householdService, invitationService, session)

        test("list は session の actor で呼ぶ") {
            every { householdService.list(residentId) } returns Households(emptyList())
            controller.list() shouldBe RpcResult.Ok(Households(emptyList()))
        }

        test("previewInvite は invitation の世帯名と付与ロールを組み立てる") {
            val householdId = HouseholdId.create()
            val code = InvitationCode("ABC123")
            every { invitationService.findByCode(code) } returns Invitation(householdId, code, HouseholdMemberRole.世帯員, InvitationValidity.有効)
            every { householdService.findById(householdId) } returns
                Household(householdId, Profile(HouseholdName("となりの家")), Members(listOf(HouseholdMember(Resident(ResidentId.create(), ResidentProfile(DisplayName("ぬし"))), HouseholdMemberRole.世帯主))))
            controller.previewInvite(code) shouldBe RpcResult.Ok(InvitationPreview(HouseholdName("となりの家"), HouseholdMemberRole.世帯員))
        }
    })
```

> `InvitationCode("ABC123")` の生成方法・`HouseholdMemberRole.世帯員` の正確な enum 値は実コードに合わせる(`InvitationCode` が `value class` で文字列 ctor を持つか、`generate()` のみかは `InvitationCode.kt` を確認。文字列 ctor が無ければ `Invitation.issue(householdId, role).code` を使う)。

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :backend:api:test --tests "*HouseholdControllerTest*"`
Expected: FAIL

- [ ] **Step 3: 実装を書く**

```kotlin
package net.brightroom.mindstock.presentation.rpc.household

import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.invitation.InvitationService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.auth.requireResidentId
import net.brightroom.mindstock.configuration.guard.guarded
import net.brightroom.mindstock.domain.model.household.Households
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.rpc.household.HouseholdRpcService
import net.brightroom.mindstock.rpc.household.InvitationPreview
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

class HouseholdController(
    private val householdService: HouseholdService,
    private val invitationService: InvitationService,
    private val session: MindstockSession,
) : HouseholdRpcService {
    override suspend fun list(): RpcResult<Households, RpcError> =
        guarded(session) { RpcResult.Ok(householdService.list(session.requireResidentId())) }

    override suspend fun previewInvite(code: InvitationCode): RpcResult<InvitationPreview, RpcError> =
        guarded(session) {
            val invitation = invitationService.findByCode(code)
            val household = householdService.findById(invitation.householdId)
            RpcResult.Ok(InvitationPreview(household.profile.name, invitation.grantedRole))
        }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :backend:api:test --tests "*HouseholdControllerTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/household/HouseholdController.kt \
        backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/rpc/household/HouseholdControllerTest.kt
git commit -m "feat(api): HouseholdController(list/previewInvite)を追加"
```

---

## Task 14: `HouseholdRegisterController`

**Files:**
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/household/HouseholdRegisterController.kt`
- Test: `backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/rpc/household/HouseholdRegisterControllerTest.kt`

`create`/`rename`/`leave`/`changeRole`/`removeMember` は `HouseholdRegisterService`、`createInvite`/`revokeInvite`/`join` は Scenario 経由。全メソッド actor を session から渡す。

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package net.brightroom.mindstock.presentation.rpc.household

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.brightroom.mindstock.application.scenario.invitation.CreateInvitationScenario
import net.brightroom.mindstock.application.scenario.invitation.RevokeInvitationScenario
import net.brightroom.mindstock.application.scenario.household.JoinHouseholdScenario
import net.brightroom.mindstock.application.service.household.HouseholdRegisterService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.Profile
import net.brightroom.mindstock.domain.model.household.member.HouseholdMember
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.member.Members
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile as ResidentProfile
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

class HouseholdRegisterControllerTest :
    FunSpec({
        val registerService = mockk<HouseholdRegisterService>(relaxed = true)
        val createInvitation = mockk<CreateInvitationScenario>()
        val revokeInvitation = mockk<RevokeInvitationScenario>(relaxed = true)
        val joinHousehold = mockk<JoinHouseholdScenario>()
        val residentId = ResidentId.create()
        val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub"))
        val session: MindstockSession = MindstockSession.Registered(identity, residentId, Clock.System.now().plus(1.hours), Uuid.random())
        val controller = HouseholdRegisterController(registerService, createInvitation, revokeInvitation, joinHousehold, session)

        fun household(id: HouseholdId) =
            Household(id, Profile(HouseholdName("わが家")), Members(listOf(HouseholdMember(Resident(residentId, ResidentProfile(DisplayName("ぬし"))), HouseholdMemberRole.世帯主))))

        test("create は actor を渡して Ok を返す") {
            val id = HouseholdId.create()
            every { registerService.create(HouseholdName("新居"), residentId) } returns household(id)
            controller.create(HouseholdName("新居")) shouldBe RpcResult.Ok(household(id))
        }

        test("rename は actor を渡す") {
            val id = HouseholdId.create()
            controller.rename(id, HouseholdName("改名")) shouldBe RpcResult.Ok(Unit)
            verify { registerService.rename(id, HouseholdName("改名"), residentId) }
        }
    })
```

> 残りのメソッド(`leave`/`changeRole`/`removeMember`/`createInvite`/`revokeInvite`/`join`)も同パターンでテストを足してよいが、最低 2 本(委譲と actor 伝播の確認)で可。`CreateInvitationScenario.run` / `JoinHouseholdScenario.run` の戻り型(`Invitation` / `Household`)は実シグネチャに合わせる。

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :backend:api:test --tests "*HouseholdRegisterControllerTest*"`
Expected: FAIL

- [ ] **Step 3: 実装を書く**

```kotlin
package net.brightroom.mindstock.presentation.rpc.household

import net.brightroom.mindstock.application.scenario.household.JoinHouseholdScenario
import net.brightroom.mindstock.application.scenario.invitation.CreateInvitationScenario
import net.brightroom.mindstock.application.scenario.invitation.RevokeInvitationScenario
import net.brightroom.mindstock.application.service.household.HouseholdRegisterService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.auth.requireResidentId
import net.brightroom.mindstock.configuration.guard.guarded
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.rpc.household.HouseholdRegisterRpcService
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

class HouseholdRegisterController(
    private val householdRegisterService: HouseholdRegisterService,
    private val createInvitationScenario: CreateInvitationScenario,
    private val revokeInvitationScenario: RevokeInvitationScenario,
    private val joinHouseholdScenario: JoinHouseholdScenario,
    private val session: MindstockSession,
) : HouseholdRegisterRpcService {
    override suspend fun create(name: HouseholdName): RpcResult<Household, RpcError> =
        guarded(session) { RpcResult.Ok(householdRegisterService.create(name, session.requireResidentId())) }

    override suspend fun rename(
        householdId: HouseholdId,
        name: HouseholdName,
    ): RpcResult<Unit, RpcError> =
        guarded(session) {
            householdRegisterService.rename(householdId, name, session.requireResidentId())
            RpcResult.Ok(Unit)
        }

    override suspend fun leave(householdId: HouseholdId): RpcResult<Unit, RpcError> =
        guarded(session) {
            householdRegisterService.leave(householdId, session.requireResidentId())
            RpcResult.Ok(Unit)
        }

    override suspend fun changeRole(
        householdId: HouseholdId,
        target: ResidentId,
        role: HouseholdMemberRole,
    ): RpcResult<Unit, RpcError> =
        guarded(session) {
            householdRegisterService.changeRole(householdId, target, role, session.requireResidentId())
            RpcResult.Ok(Unit)
        }

    override suspend fun removeMember(
        householdId: HouseholdId,
        target: ResidentId,
    ): RpcResult<Unit, RpcError> =
        guarded(session) {
            householdRegisterService.removeMember(householdId, target, session.requireResidentId())
            RpcResult.Ok(Unit)
        }

    override suspend fun createInvite(
        householdId: HouseholdId,
        role: HouseholdMemberRole,
    ): RpcResult<Invitation, RpcError> =
        guarded(session) { RpcResult.Ok(createInvitationScenario.run(householdId, role, session.requireResidentId())) }

    override suspend fun revokeInvite(code: InvitationCode): RpcResult<Unit, RpcError> =
        guarded(session) {
            revokeInvitationScenario.run(code, session.requireResidentId())
            RpcResult.Ok(Unit)
        }

    override suspend fun join(code: InvitationCode): RpcResult<Household, RpcError> =
        guarded(session) { RpcResult.Ok(joinHouseholdScenario.run(code, session.requireResidentId())) }
}
```

> 各メソッドのシグネチャは `HouseholdRegisterRpcService.kt` と完全一致させる(`changeRole(householdId, target, role)` の引数順など)。Scenario の `run` 引数順(`createInvitationScenario.run(householdId, role, actor)` / `revokeInvitationScenario.run(code, actor)` / `joinHouseholdScenario.run(code, actor)`)も実シグネチャに合わせる。

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :backend:api:test --tests "*HouseholdRegisterControllerTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/household/HouseholdRegisterController.kt \
        backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/rpc/household/HouseholdRegisterControllerTest.kt
git commit -m "feat(api): HouseholdRegisterController を追加"
```

---

## Task 15: `ProductController`

**Files:**
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/product/ProductController.kt`
- Test: `backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/rpc/product/ProductControllerTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package net.brightroom.mindstock.presentation.rpc.product

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import net.brightroom.mindstock.application.service.product.ProductService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

class ProductControllerTest :
    FunSpec({
        val productService = mockk<ProductService>()
        val productRegisterService = mockk<net.brightroom.mindstock.application.service.product.ProductRegisterService>()
        val adoptScenario = mockk<net.brightroom.mindstock.application.scenario.product.AdoptProductScenario>()
        val residentId = ResidentId.create()
        val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub"))
        val session: MindstockSession = MindstockSession.Registered(identity, residentId, Clock.System.now().plus(1.hours), Uuid.random())
        val controller = ProductController(productService, session)

        test("list は householdId と session の actor で呼ぶ") {
            val householdId = HouseholdId.create()
            every { productService.list(householdId, residentId) } returns Stocks(emptyList())
            controller.list(householdId) shouldBe RpcResult.Ok(Stocks(emptyList()))
        }
    })
```

> 注: `ProductController` は read 系(`ProductRpcService`)のみ。`adopt` 等の write は `ProductRegisterController`(Task 16)。本テストの未使用 mock(`productRegisterService`/`adoptScenario`)行は削除してよい(上は説明用)。

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :backend:api:test --tests "*ProductControllerTest*"`
Expected: FAIL

- [ ] **Step 3: 実装を書く**

```kotlin
package net.brightroom.mindstock.presentation.rpc.product

import net.brightroom.mindstock.application.service.product.ProductService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.auth.requireResidentId
import net.brightroom.mindstock.configuration.guard.guarded
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Products
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingList
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.rpc.product.ProductRpcService
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

class ProductController(
    private val productService: ProductService,
    private val session: MindstockSession,
) : ProductRpcService {
    override suspend fun list(householdId: HouseholdId): RpcResult<Stocks, RpcError> =
        guarded(session) { RpcResult.Ok(productService.list(householdId, session.requireResidentId())) }

    override suspend fun listArchived(householdId: HouseholdId): RpcResult<Products, RpcError> =
        guarded(session) { RpcResult.Ok(productService.listArchived(householdId, session.requireResidentId())) }

    override suspend fun shoppingList(householdId: HouseholdId): RpcResult<ShoppingList, RpcError> =
        guarded(session) { RpcResult.Ok(productService.shoppingList(householdId, session.requireResidentId())) }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :backend:api:test --tests "*ProductControllerTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/product/ProductController.kt \
        backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/rpc/product/ProductControllerTest.kt
git commit -m "feat(api): ProductController(list/listArchived/shoppingList)を追加"
```

---

## Task 16: `ProductRegisterController`

**Files:**
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/product/ProductRegisterController.kt`
- Test: `backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/rpc/product/ProductRegisterControllerTest.kt`

`adopt` は `AdoptProductScenario`、それ以外は `ProductRegisterService`。`addCustom` は `AddCustomProductRequest` を分解して service に渡す。

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package net.brightroom.mindstock.presentation.rpc.product

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.brightroom.mindstock.application.scenario.product.AdoptProductScenario
import net.brightroom.mindstock.application.service.product.ProductRegisterService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import net.brightroom.mindstock.rpc.product.AddCustomProductRequest
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

class ProductRegisterControllerTest :
    FunSpec({
        val productRegisterService = mockk<ProductRegisterService>(relaxed = true)
        val adoptScenario = mockk<AdoptProductScenario>()
        val residentId = ResidentId.create()
        val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub"))
        val session: MindstockSession = MindstockSession.Registered(identity, residentId, Clock.System.now().plus(1.hours), Uuid.random())
        val controller = ProductRegisterController(productRegisterService, adoptScenario, session)

        test("adopt は scenario に actor を渡す") {
            val householdId = HouseholdId.create()
            val catalogItemId = CatalogItemId.create()
            val product = Product.custom(ProductName("水"), Barcode.Unlinked, ProductUnit("本"), MinimumStock(1))
            every { adoptScenario.run(householdId, catalogItemId, ProductUnit("本"), MinimumStock(1), residentId) } returns product
            controller.adopt(householdId, catalogItemId, ProductUnit("本"), MinimumStock(1)) shouldBe RpcResult.Ok(product)
        }

        test("changeUnit は service に actor を渡す") {
            val productId = ProductId.create()
            controller.changeUnit(productId, ProductUnit("缶")) shouldBe RpcResult.Ok(Unit)
            verify { productRegisterService.changeUnit(productId, ProductUnit("缶"), residentId) }
        }
    })
```

> `CatalogItemId.create()` の有無は実コードに合わせる(無ければ `CatalogItemId(Uuid.generateV7())` 等)。`AddCustomProductRequest` を使う `addCustom` のテストは任意で追加。

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :backend:api:test --tests "*ProductRegisterControllerTest*"`
Expected: FAIL

- [ ] **Step 3: 実装を書く**

```kotlin
package net.brightroom.mindstock.presentation.rpc.product

import net.brightroom.mindstock.application.scenario.product.AdoptProductScenario
import net.brightroom.mindstock.application.service.product.ProductRegisterService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.auth.requireResidentId
import net.brightroom.mindstock.configuration.guard.guarded
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.image.ProductImage
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.rpc.product.AddCustomProductRequest
import net.brightroom.mindstock.rpc.product.ProductRegisterRpcService
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

class ProductRegisterController(
    private val productRegisterService: ProductRegisterService,
    private val adoptProductScenario: AdoptProductScenario,
    private val session: MindstockSession,
) : ProductRegisterRpcService {
    override suspend fun adopt(
        householdId: HouseholdId,
        catalogItemId: CatalogItemId,
        unit: ProductUnit,
        minimumStock: MinimumStock,
    ): RpcResult<Product, RpcError> =
        guarded(session) {
            RpcResult.Ok(adoptProductScenario.run(householdId, catalogItemId, unit, minimumStock, session.requireResidentId()))
        }

    override suspend fun addCustom(
        householdId: HouseholdId,
        request: AddCustomProductRequest,
    ): RpcResult<Product, RpcError> =
        guarded(session) {
            RpcResult.Ok(
                productRegisterService.addCustom(
                    householdId,
                    request.name,
                    request.barcode,
                    request.unit,
                    request.minimumStock,
                    session.requireResidentId(),
                ),
            )
        }

    override suspend fun changeUnit(
        productId: ProductId,
        unit: ProductUnit,
    ): RpcResult<Unit, RpcError> =
        guarded(session) {
            productRegisterService.changeUnit(productId, unit, session.requireResidentId())
            RpcResult.Ok(Unit)
        }

    override suspend fun changeImage(
        productId: ProductId,
        image: ProductImage,
    ): RpcResult<Unit, RpcError> =
        guarded(session) {
            productRegisterService.changeImage(productId, image, session.requireResidentId())
            RpcResult.Ok(Unit)
        }

    override suspend fun changeMinimum(
        productId: ProductId,
        minimumStock: MinimumStock,
    ): RpcResult<Unit, RpcError> =
        guarded(session) {
            productRegisterService.changeMinimum(productId, minimumStock, session.requireResidentId())
            RpcResult.Ok(Unit)
        }

    override suspend fun archive(productId: ProductId): RpcResult<Unit, RpcError> =
        guarded(session) {
            productRegisterService.archive(productId, session.requireResidentId())
            RpcResult.Ok(Unit)
        }

    override suspend fun unarchive(productId: ProductId): RpcResult<Unit, RpcError> =
        guarded(session) {
            productRegisterService.unarchive(productId, session.requireResidentId())
            RpcResult.Ok(Unit)
        }

    override suspend fun setWanted(
        productId: ProductId,
        wanted: Boolean,
    ): RpcResult<Unit, RpcError> =
        guarded(session) {
            productRegisterService.setWanted(productId, wanted, session.requireResidentId())
            RpcResult.Ok(Unit)
        }
}
```

> `AddCustomProductRequest` の field 名(`name`/`unit`/`barcode`/`minimumStock`)と `addCustom` の引数順を実シグネチャに厳密一致させる(spec 確認済: `addCustom(householdId, name, barcode, unit, minimumStock, actor)`)。`ProductRegisterRpcService` のメソッドシグネチャ順も完全一致させる。

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :backend:api:test --tests "*ProductRegisterControllerTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/product/ProductRegisterController.kt \
        backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/rpc/product/ProductRegisterControllerTest.kt
git commit -m "feat(api): ProductRegisterController を追加"
```

---

## Task 17: `StockController`(history / activity → ActivityFeed)

**Files:**
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/stock/StockController.kt`
- Test: `backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/rpc/stock/StockControllerTest.kt`

`activity` は `StockService.activity(householdId, actor): Stocks` を `ActivityFeed`(`List<ActivityEntry(product, movement)>`)に flatten する。各 Stock の `movements.list` を展開し、`occurredAt()` 降順で並べる。

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package net.brightroom.mindstock.presentation.rpc.stock

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import net.brightroom.mindstock.application.service.stock.StockService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementIdentity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import net.brightroom.mindstock.rpc.result.RpcResult
import net.brightroom.mindstock.rpc.stock.ActivityEntry
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

class StockControllerTest :
    FunSpec({
        val stockService = mockk<StockService>()
        val residentId = ResidentId.create()
        val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub"))
        val session: MindstockSession = MindstockSession.Registered(identity, residentId, Clock.System.now().plus(1.hours), Uuid.random())
        val controller = StockController(stockService, session)

        test("activity は Stock 群を ActivityFeed に flatten する") {
            val householdId = HouseholdId.create()
            val actorRes = Resident(residentId, Profile(DisplayName("じぶん")))
            val product = Product.custom(ProductName("水"), Barcode.Unlinked, ProductUnit("本"), MinimumStock(1))
            val mv = StockMovement.Replenishment(MovementIdentity.Pending, Quantity(3), OccurredAt.now(), actorRes, Note(""))
            every { stockService.activity(householdId, residentId) } returns Stocks(listOf(Stock(product, StockMovements(listOf(mv)))))

            val result = controller.activity(householdId)
            val feed = (result as RpcResult.Ok).value
            feed.list shouldBe listOf(ActivityEntry(product, mv))
        }
    })
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :backend:api:test --tests "*StockControllerTest*"`
Expected: FAIL

- [ ] **Step 3: 実装を書く**

```kotlin
package net.brightroom.mindstock.presentation.rpc.stock

import net.brightroom.mindstock.application.service.stock.StockService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.auth.requireResidentId
import net.brightroom.mindstock.configuration.guard.guarded
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult
import net.brightroom.mindstock.rpc.stock.ActivityEntry
import net.brightroom.mindstock.rpc.stock.ActivityFeed
import net.brightroom.mindstock.rpc.stock.StockRpcService

class StockController(
    private val stockService: StockService,
    private val session: MindstockSession,
) : StockRpcService {
    override suspend fun history(productId: ProductId): RpcResult<StockMovements, RpcError> =
        guarded(session) { RpcResult.Ok(stockService.history(productId, session.requireResidentId())) }

    override suspend fun activity(householdId: HouseholdId): RpcResult<ActivityFeed, RpcError> =
        guarded(session) {
            val stocks = stockService.activity(householdId, session.requireResidentId())
            val entries =
                stocks.list
                    .flatMap { stock -> stock.movements.list.map { ActivityEntry(stock.product, it) } }
                    .sortedByDescending { it.movement.occurredAt() }
            RpcResult.Ok(ActivityFeed(entries))
        }
}
```

> `StockMovement.occurredAt()` accessor の存在は `StockMovement.kt` で確認(`StockMovements.netQuantity` 内で `it.occurredAt()` を使用済)。テストは順序を 1 件で検証するため `sortedByDescending` の安定性に依存しない。

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :backend:api:test --tests "*StockControllerTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/stock/StockController.kt \
        backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/rpc/stock/StockControllerTest.kt
git commit -m "feat(api): StockController(history/activity->ActivityFeed)を追加"
```

---

## Task 18: `StockRegisterController`

**Files:**
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/stock/StockRegisterController.kt`
- Test: `backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/rpc/stock/StockRegisterControllerTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package net.brightroom.mindstock.presentation.rpc.stock

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import net.brightroom.mindstock.application.service.stock.StockRegisterService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

class StockRegisterControllerTest :
    FunSpec({
        val service = mockk<StockRegisterService>(relaxed = true)
        val residentId = ResidentId.create()
        val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub"))
        val session: MindstockSession = MindstockSession.Registered(identity, residentId, Clock.System.now().plus(1.hours), Uuid.random())
        val controller = StockRegisterController(service, session)

        test("replenish は service に actor を渡して Ok") {
            val productId = ProductId.create()
            controller.replenish(productId, Quantity(3), Note("補充")) shouldBe RpcResult.Ok(Unit)
            verify { service.replenish(productId, Quantity(3), Note("補充"), residentId) }
        }
    })
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :backend:api:test --tests "*StockRegisterControllerTest*"`
Expected: FAIL

- [ ] **Step 3: 実装を書く**

```kotlin
package net.brightroom.mindstock.presentation.rpc.stock

import net.brightroom.mindstock.application.service.stock.StockRegisterService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.auth.requireResidentId
import net.brightroom.mindstock.configuration.guard.guarded
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Reason
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult
import net.brightroom.mindstock.rpc.stock.StockRegisterRpcService

class StockRegisterController(
    private val stockRegisterService: StockRegisterService,
    private val session: MindstockSession,
) : StockRegisterRpcService {
    override suspend fun replenish(
        productId: ProductId,
        quantity: Quantity,
        note: Note,
    ): RpcResult<Unit, RpcError> =
        guarded(session) {
            stockRegisterService.replenish(productId, quantity, note, session.requireResidentId())
            RpcResult.Ok(Unit)
        }

    override suspend fun consume(
        productId: ProductId,
        quantity: Quantity,
        note: Note,
    ): RpcResult<Unit, RpcError> =
        guarded(session) {
            stockRegisterService.consume(productId, quantity, note, session.requireResidentId())
            RpcResult.Ok(Unit)
        }

    override suspend fun correct(
        target: MovementId,
        correctedQuantity: Quantity,
        reason: Reason,
    ): RpcResult<Unit, RpcError> =
        guarded(session) {
            stockRegisterService.correct(target, correctedQuantity, reason, session.requireResidentId())
            RpcResult.Ok(Unit)
        }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :backend:api:test --tests "*StockRegisterControllerTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/stock/StockRegisterController.kt \
        backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/rpc/stock/StockRegisterControllerTest.kt
git commit -m "feat(api): StockRegisterController を追加"
```

---

## Task 19: `ExposedConfiguration`(DB 接続)

**Files:**
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/external/exposed/ExposedDataSourceProperties.kt`
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/external/exposed/ExposedConfiguration.kt`

旧 `11c9b31` から移植。新 DataSource は `Database` 注入なので `provide<Database>` を提供する(DI 登録は Task 21 でも参照)。コンパイルのみ確認。

- [ ] **Step 1: `ExposedDataSourceProperties.kt` を書く**

```kotlin
package net.brightroom.mindstock.configuration.external.exposed

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExposedDataSourceProperties(
    @SerialName("driver-class-name") val driverClassName: String,
    @SerialName("jdbc-url") val jdbcUrl: String,
    @SerialName("username") val username: String,
    @SerialName("password") val password: String,
    @SerialName("maximum-pool-size") val maximumPoolSize: Int = 10,
    @SerialName("auto-commit") val autoCommit: Boolean = false,
    @SerialName("transaction-isolation") val transactionIsolation: String = "TRANSACTION_REPEATABLE_READ",
) {
    override fun toString(): String =
        "ExposedDataSourceProperties(driverClassName=$driverClassName, jdbcUrl=$jdbcUrl, " +
            "username=$username, password=***, maximumPoolSize=$maximumPoolSize, " +
            "autoCommit=$autoCommit, transactionIsolation=$transactionIsolation)"
}
```

- [ ] **Step 2: `ExposedConfiguration.kt` を書く**

```kotlin
package net.brightroom.mindstock.configuration.external.exposed

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.plugins.di.annotations.Property
import io.ktor.server.plugins.di.dependencies
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database

fun Application.exposedConfigure(
    @Property("external.datasource.database") properties: ExposedDataSourceProperties,
) {
    val hikariConfig =
        HikariConfig().apply {
            driverClassName = properties.driverClassName
            jdbcUrl = properties.jdbcUrl
            username = properties.username
            password = properties.password
            maximumPoolSize = properties.maximumPoolSize
            isAutoCommit = properties.autoCommit
            transactionIsolation = properties.transactionIsolation
        }

    val dataSource = HikariDataSource(hikariConfig)

    monitor.subscribe(ApplicationStopped) {
        dataSource.close()
    }

    dependencies {
        provide<Database> {
            Database.connect(
                datasource = dataSource,
                databaseConfig = DatabaseConfig.invoke { useNestedTransactions = true },
            )
        }
    }
}
```

> `org.jetbrains.exposed.v1.jdbc.Database` / `org.jetbrains.exposed.v1.core.DatabaseConfig` は既存 DataSource と同じ Exposed v1 package。`import` が解決しない場合は既存 DataSource の import を参照。

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :backend:api:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/external/exposed/
git commit -m "feat(api): ExposedConfiguration(Hikari+Exposed Database)を追加"
```

---

## Task 20: `MigrationConfiguration`(Flyway)

**Files:**
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/migration/MigrationConfiguration.kt`

`backend/core` の `db/migration/V1__init.sql` を起動時に適用(`backend/api` は `backend/core` に依存 → classpath に乗る)。

- [ ] **Step 1: 実装を書く**

```kotlin
package net.brightroom.mindstock.configuration.migration

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.annotations.Property
import net.brightroom.mindstock.configuration.external.exposed.ExposedDataSourceProperties
import org.flywaydb.core.Flyway

fun Application.migrationConfigure(
    @Property("external.datasource.database") properties: ExposedDataSourceProperties,
) {
    val hikariConfig =
        HikariConfig().apply {
            driverClassName = properties.driverClassName
            jdbcUrl = properties.jdbcUrl
            username = properties.username
            password = properties.password
            maximumPoolSize = 2
            isAutoCommit = false
        }

    HikariDataSource(hikariConfig).use { dataSource ->
        Flyway
            .configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }
}
```

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :backend:api:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/migration/MigrationConfiguration.kt
git commit -m "feat(api): MigrationConfiguration(Flyway 起動時マイグレーション)を追加"
```

---

## Task 21: `DependenciesConfiguration`(DI 配線)

**Files:**
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/di/DependenciesConfiguration.kt`

Repository(DataSource は `Database` 注入)→ Service → Scenario を DI 登録。Controller は routing でインライン構築するため登録不要。

- [ ] **Step 1: 実装を書く**

```kotlin
package net.brightroom.mindstock.configuration.di

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import net.brightroom.mindstock.application.repository.catalog.CatalogRegisterRepository
import net.brightroom.mindstock.application.repository.catalog.CatalogRepository
import net.brightroom.mindstock.application.repository.household.HouseholdRegisterRepository
import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.invitation.InvitationRegisterRepository
import net.brightroom.mindstock.application.repository.invitation.InvitationRepository
import net.brightroom.mindstock.application.repository.product.ProductRegisterRepository
import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.resident.ResidentRegisterRepository
import net.brightroom.mindstock.application.repository.resident.ResidentRepository
import net.brightroom.mindstock.application.repository.stock.StockRegisterRepository
import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.application.scenario.household.JoinHouseholdScenario
import net.brightroom.mindstock.application.scenario.invitation.CreateInvitationScenario
import net.brightroom.mindstock.application.scenario.invitation.RevokeInvitationScenario
import net.brightroom.mindstock.application.scenario.product.AdoptProductScenario
import net.brightroom.mindstock.application.service.catalog.CatalogService
import net.brightroom.mindstock.application.service.household.HouseholdRegisterService
import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.invitation.InvitationRegisterService
import net.brightroom.mindstock.application.service.invitation.InvitationService
import net.brightroom.mindstock.application.service.product.ProductRegisterService
import net.brightroom.mindstock.application.service.product.ProductService
import net.brightroom.mindstock.application.service.resident.ResidentRegisterService
import net.brightroom.mindstock.application.service.resident.ResidentService
import net.brightroom.mindstock.application.service.stock.StockRegisterService
import net.brightroom.mindstock.application.service.stock.StockService
import net.brightroom.mindstock.infrastructure.datasource.catalog.CatalogDataSource
import net.brightroom.mindstock.infrastructure.datasource.catalog.CatalogRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.household.HouseholdDataSource
import net.brightroom.mindstock.infrastructure.datasource.household.HouseholdRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.invitation.InvitationDataSource
import net.brightroom.mindstock.infrastructure.datasource.invitation.InvitationRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.product.ProductDataSource
import net.brightroom.mindstock.infrastructure.datasource.product.ProductRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.resident.ResidentDataSource
import net.brightroom.mindstock.infrastructure.datasource.resident.ResidentRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.stock.StockDataSource
import net.brightroom.mindstock.infrastructure.datasource.stock.StockRegisterDataSource
import net.brightroom.mindstock.infrastructure.gateway.ExternalProductGateway
import net.brightroom.mindstock.infrastructure.gateway.UnconfiguredProductGateway

fun Application.dependenciesConfigure() {
    dependencies {
        // Repository(DataSource は Database を注入)
        provide<ResidentRepository> { ResidentDataSource(resolve()) }
        provide<ResidentRegisterRepository> { ResidentRegisterDataSource(resolve()) }
        provide<CatalogRepository> { CatalogDataSource(resolve()) }
        provide<CatalogRegisterRepository> { CatalogRegisterDataSource(resolve()) }
        provide<HouseholdRepository> { HouseholdDataSource(resolve()) }
        provide<HouseholdRegisterRepository> { HouseholdRegisterDataSource(resolve()) }
        provide<InvitationRepository> { InvitationDataSource(resolve()) }
        provide<InvitationRegisterRepository> { InvitationRegisterDataSource(resolve()) }
        provide<ProductRepository> { ProductDataSource(resolve()) }
        provide<ProductRegisterRepository> { ProductRegisterDataSource(resolve()) }
        provide<StockRepository> { StockDataSource(resolve(), resolve()) }
        provide<StockRegisterRepository> { StockRegisterDataSource(resolve()) }

        // Gateway
        provide<ExternalProductGateway> { UnconfiguredProductGateway() }

        // Service
        provide<ResidentService> { ResidentService(resolve()) }
        provide<ResidentRegisterService> { ResidentRegisterService(resolve()) }
        provide<CatalogService> { CatalogService(resolve(), resolve(), resolve()) }
        provide<HouseholdService> { HouseholdService(resolve()) }
        provide<HouseholdRegisterService> { HouseholdRegisterService(resolve(), resolve(), resolve()) }
        provide<InvitationService> { InvitationService(resolve()) }
        provide<InvitationRegisterService> { InvitationRegisterService(resolve()) }
        provide<ProductService> { ProductService(resolve(), resolve(), resolve()) }
        provide<ProductRegisterService> { ProductRegisterService(resolve(), resolve(), resolve(), resolve()) }
        provide<StockService> { StockService(resolve(), resolve(), resolve()) }
        provide<StockRegisterService> { StockRegisterService(resolve(), resolve(), resolve(), resolve(), resolve()) }

        // Scenario
        provide<AdoptProductScenario> { AdoptProductScenario(resolve(), resolve()) }
        provide<JoinHouseholdScenario> { JoinHouseholdScenario(resolve(), resolve(), resolve()) }
        provide<CreateInvitationScenario> { CreateInvitationScenario(resolve(), resolve()) }
        provide<RevokeInvitationScenario> { RevokeInvitationScenario(resolve(), resolve(), resolve()) }
    }
}
```

> **`resolve()` の個数は各クラスのコンストラクタ引数と完全一致させること**(本 plan 執筆時点の確認値):
> - `CatalogService`(3: catalogRepository, catalogRegisterRepository, externalProductGateway)
> - `HouseholdRegisterService`(3: residentRepository, householdRepository, householdRegisterRepository)
> - `ProductService`(3: stockRepository, productRepository, householdRepository ← Task 3)
> - `ProductRegisterService`(4: productRepository, productRegisterRepository, stockRepository, householdRepository ← Task 5)
> - `StockService`(3: stockRepository, productRepository, householdRepository ← Task 4)
> - `StockRegisterService`(5: residentRepository, stockRepository, stockRegisterRepository, householdRepository, productRepository ← Task 6)
> - `StockDataSource`(2: database, productDataSource)
> - `InvitationService`(1)/`InvitationRegisterService`(1)/`ResidentService`(1)/`ResidentRegisterService`(1)/`HouseholdService`(1)
> - Scenario: `AdoptProductScenario`(2)/`JoinHouseholdScenario`(3)/`CreateInvitationScenario`(2)/`RevokeInvitationScenario`(3)
> Ktor DI は型で解決するため `resolve()` は引数型から推論される。コンパイルが通れば配線は型整合。

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :backend:api:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/di/DependenciesConfiguration.kt
git commit -m "feat(api): DependenciesConfiguration(Repository/Service/Scenario の DI 配線)を追加"
```

---

## Task 22: `RoutingConfiguration`(全面差し替え)

**Files:**
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/routing/RoutingConfiguration.kt`

- [ ] **Step 1: 全面置換**

```kotlin
@file:OptIn(ExperimentalSerializationApi::class)

package net.brightroom.mindstock.configuration.routing

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.serialization.kotlinx.json.jsonIo
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.rpc.krpc.ktor.server.Krpc
import kotlinx.rpc.krpc.ktor.server.rpc
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import net.brightroom.mindstock.application.repository.resident.ResidentRepository
import net.brightroom.mindstock.application.scenario.household.JoinHouseholdScenario
import net.brightroom.mindstock.application.scenario.invitation.CreateInvitationScenario
import net.brightroom.mindstock.application.scenario.invitation.RevokeInvitationScenario
import net.brightroom.mindstock.application.scenario.product.AdoptProductScenario
import net.brightroom.mindstock.application.service.catalog.CatalogService
import net.brightroom.mindstock.application.service.household.HouseholdRegisterService
import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.invitation.InvitationService
import net.brightroom.mindstock.application.service.product.ProductRegisterService
import net.brightroom.mindstock.application.service.product.ProductService
import net.brightroom.mindstock.application.service.resident.ResidentRegisterService
import net.brightroom.mindstock.application.service.resident.ResidentService
import net.brightroom.mindstock.application.service.stock.StockRegisterService
import net.brightroom.mindstock.application.service.stock.StockService
import net.brightroom.mindstock.configuration.auth.MindstockAuthPlugin
import net.brightroom.mindstock.configuration.auth.RequireRegisteredUserPlugin
import net.brightroom.mindstock.configuration.auth.WsSubprotocolEchoPlugin
import net.brightroom.mindstock.configuration.auth.sessionOf
import net.brightroom.mindstock.extensions.kotlinx.serialization.CustomJson
import net.brightroom.mindstock.extensions.kotlinx.serialization.KrpcJson
import net.brightroom.mindstock.presentation.rpc.catalog.CatalogController
import net.brightroom.mindstock.presentation.rpc.household.HouseholdController
import net.brightroom.mindstock.presentation.rpc.household.HouseholdRegisterController
import net.brightroom.mindstock.presentation.rpc.product.ProductController
import net.brightroom.mindstock.presentation.rpc.product.ProductRegisterController
import net.brightroom.mindstock.presentation.rpc.resident.ResidentController
import net.brightroom.mindstock.presentation.rpc.resident.ResidentRegisterController
import net.brightroom.mindstock.presentation.rpc.stock.StockController
import net.brightroom.mindstock.presentation.rpc.stock.StockRegisterController
import net.brightroom.mindstock.rpc.catalog.CatalogRpcService
import net.brightroom.mindstock.rpc.household.HouseholdRegisterRpcService
import net.brightroom.mindstock.rpc.household.HouseholdRpcService
import net.brightroom.mindstock.rpc.product.ProductRegisterRpcService
import net.brightroom.mindstock.rpc.product.ProductRpcService
import net.brightroom.mindstock.rpc.resident.ResidentRegisterRpcService
import net.brightroom.mindstock.rpc.resident.ResidentRpcService
import net.brightroom.mindstock.rpc.stock.StockRegisterRpcService
import net.brightroom.mindstock.rpc.stock.StockRpcService
import java.net.URI
import java.util.concurrent.TimeUnit

fun Application.routingConfigure() {
    install(ContentNegotiation) { jsonIo(CustomJson) }
    install(Krpc) { serialization { json(KrpcJson) } }
    install(WsSubprotocolEchoPlugin)

    val authConfig = environment.config.config("external.auth")
    val residentRepository: ResidentRepository by dependencies

    install(MindstockAuthPlugin) {
        jwkProvider =
            JwkProviderBuilder(URI(authConfig.property("jwks-url").getString()).toURL())
                .cached(10, 1, TimeUnit.HOURS)
                .rateLimited(10, 1, TimeUnit.MINUTES)
                .build()
        issuer = authConfig.property("issuer").getString()
        audience = authConfig.property("audience").getString()
        this.residentRepository = residentRepository
    }

    // service / scenario を先取り解決(registerService factory は非 suspend のため)
    val residentService: ResidentService by dependencies
    val residentRegisterService: ResidentRegisterService by dependencies
    val catalogService: CatalogService by dependencies
    val householdService: HouseholdService by dependencies
    val householdRegisterService: HouseholdRegisterService by dependencies
    val invitationService: InvitationService by dependencies
    val productService: ProductService by dependencies
    val productRegisterService: ProductRegisterService by dependencies
    val stockService: StockService by dependencies
    val stockRegisterService: StockRegisterService by dependencies
    val adoptProductScenario: AdoptProductScenario by dependencies
    val createInvitationScenario: CreateInvitationScenario by dependencies
    val revokeInvitationScenario: RevokeInvitationScenario by dependencies
    val joinHouseholdScenario: JoinHouseholdScenario by dependencies

    routing {
        route("/api/v1") {
            // public: JWT 有効なら未登録 OK(初回登録)
            rpc("/resident/register") {
                registerService<ResidentRegisterRpcService> {
                    ResidentRegisterController(residentRegisterService, sessionOf(applicationCall))
                }
            }
            // 登録済み Resident 必須
            route("") {
                install(RequireRegisteredUserPlugin)

                rpc("/resident") {
                    registerService<ResidentRpcService> { ResidentController(residentService, sessionOf(applicationCall)) }
                }
                rpc("/catalog") {
                    registerService<CatalogRpcService> { CatalogController(catalogService, sessionOf(applicationCall)) }
                }
                rpc("/household") {
                    registerService<HouseholdRpcService> { HouseholdController(householdService, invitationService, sessionOf(applicationCall)) }
                }
                rpc("/household/register") {
                    registerService<HouseholdRegisterRpcService> {
                        HouseholdRegisterController(householdRegisterService, createInvitationScenario, revokeInvitationScenario, joinHouseholdScenario, sessionOf(applicationCall))
                    }
                }
                rpc("/product") {
                    registerService<ProductRpcService> { ProductController(productService, sessionOf(applicationCall)) }
                }
                rpc("/product/register") {
                    registerService<ProductRegisterRpcService> { ProductRegisterController(productRegisterService, adoptProductScenario, sessionOf(applicationCall)) }
                }
                rpc("/stock") {
                    registerService<StockRpcService> { StockController(stockService, sessionOf(applicationCall)) }
                }
                rpc("/stock/register") {
                    registerService<StockRegisterRpcService> { StockRegisterController(stockRegisterService, sessionOf(applicationCall)) }
                }
            }
        }
    }
}
```

> - `applicationCall` は `kotlinx.rpc.krpc.ktor.server` の `rpc{}` レシーバが提供する `ApplicationCall`(`rpc-and-transactions.md` 参照)。コンパイルエラーになる場合は import `kotlinx.rpc.krpc.ktor.server.applicationCall` を追加。
> - 旧 `RoutingConfiguration` の `@Property("ktor.environment") environment: Environment` 引数は削除(本実装では不要)。`application.yaml` の module 登録名は変わらず `routingConfigure`。
> - `route("")` がパスマッチで問題になる場合は旧実装同様 `route("/")` にフォールバック。

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :backend:api:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/routing/RoutingConfiguration.kt
git commit -m "feat(api): RoutingConfiguration(Krpc+認証+Controller 配線)を実装"
```

---

## Task 23: `application.yaml`(module 登録 + 環境変数)

**Files:**
- Modify: `backend/api/src/main/resources/application.yaml`

- [ ] **Step 1: 全面置換**

```yaml
ktor:
  environment: "$KTOR_ENV:LOCAL"
  deployment:
    port: "$PORT:8080"
  application:
    modules:
      - "net.brightroom.mindstock.configuration.migration.MigrationConfigurationKt.migrationConfigure"
      - "net.brightroom.mindstock.configuration.external.exposed.ExposedConfigurationKt.exposedConfigure"
      - "net.brightroom.mindstock.configuration.di.DependenciesConfigurationKt.dependenciesConfigure"
      - "net.brightroom.mindstock.configuration.routing.RoutingConfigurationKt.routingConfigure"

external:
  auth:
    issuer: "$AUTH_ISSUER"
    audience: "$AUTH_AUDIENCE"
    jwks-url: "$AUTH_JWKS_URL"
  datasource:
    database:
      driver-class-name: "org.postgresql.Driver"
      jdbc-url: "$DB_JDBC_URL:jdbc:postgresql://localhost:5432/mindstock"
      username: "$DB_USERNAME:mindstock"
      password: "$DB_PASSWORD:mindstock"
```

> module 実行順は宣言順: migration → exposed → dependencies → routing。`routingConfigure` は `Database`/repository を `dependencies` 解決するため最後。`$VAR:default` 構文は Ktor の config 解決(default 値付き環境変数)。

- [ ] **Step 2: Commit**

```bash
git add backend/api/src/main/resources/application.yaml
git commit -m "feat(api): application.yaml に module 登録と external.auth/datasource 設定を追加"
```

---

## Task 24: 全体検証(完了の定義)

**Files:** なし(検証のみ)

- [ ] **Step 1: domain ビルド**

Run: `./gradlew :domain:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: core テスト**

Run: `./gradlew :backend:core:test`
Expected: PASS(Task 3-7 の認可テスト含む全 green)

- [ ] **Step 3: api ビルド(統合テスト除く)**

Run: `./gradlew :backend:api:build -x integrationTest`
Expected: BUILD SUCCESSFUL(全 Controller・guarded・配線がコンパイル + DI グラフ型整合 + 全単体テスト green)

- [ ] **Step 4: spotless 適用**

Run: `./gradlew spotlessApply`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: spotless 差分があればコミット**

```bash
git add -A
git commit -m "style: spotless apply" || echo "no changes"
```

- [ ] **Step 6: 手動起動確認(任意・実環境)**

実 PostgreSQL を起動し、環境変数(`DB_*` / `AUTH_*`)を設定した上で:

Run: `./gradlew :backend:api:run`
Expected: Flyway マイグレーション適用 → DI 解決 → `:8080` で待受開始(エラーログ無し)。`GET /health` 相当の疎通は P5c では未配線のため、起動ログで判断する(WS RPC の疎通確認は frontend 接続の P6 で行う)。

> この Step は実 DB/JWKS が必要なため自動化対象外。CI/自動テストは Step 1-3 で担保する。

---

## P6 への申し送り

- frontend(Kotlin/Wasm)から kotlinx-rpc client で `/api/v1/*` に WS 接続(`Sec-WebSocket-Protocol: mindstock.v1, mindstock.bearer.<b64(jwt)>`)
- 初回ログイン(`Unregistered`)は `/api/v1/resident/register` の `registerDisplayName` で登録 → 以降 `Registered`
- RPC over WS の e2e 疎通は frontend 結合時に実施(`testApplication` の Upgrade 制約のため backend 単体では未カバー)
