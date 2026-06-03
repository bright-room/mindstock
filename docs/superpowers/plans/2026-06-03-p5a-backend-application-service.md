# P5a backend application Service 層 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** P3 で確定した `@Rpc` 契約をバックエンドで満たすための application 層(Service / Scenario)と `ExternalProductGateway` を `:backend:core` に実装し、`:backend:core:build` を緑にする。

**Architecture:** 薄い orchestration の Service(RPC に対称な Register系/参照系)+ 複数コンテキスト横断の Scenario。ビジネスロジックは domain に置き、Service/Scenario は集約 fetch → domain メソッド → 永続化を並べるだけ。`:backend:core` は `:rpc`(`RpcError`/`RpcResult`)・`MindstockSession` に依存しない(domain 型のみ授受)。トランザクション境界は DataSource 自前(P4 確定)。

**Tech Stack:** Kotlin/JVM、Exposed(既存 DataSource パターン)、Kotest FunSpec + mockk(backend JVM テスト)、kotlinx-datetime。

**Spec:** `docs/superpowers/specs/2026-06-03-p5a-backend-application-service-design.md`

---

## 設計上の前提(着手前に必読)

- **テストは「意味のあるもの」だけ書く**(`.claude/rules/testing.md`)。分岐・計算・前提崩れ例外・合成ロジックは書く。単純委譲(`me`/`list`/`history`/`rename` 等)・値保持は書かない。テストの無いクラスがあってよい。
- **DataSource 追加分(`existsByJan`/`listWanted`/`findByMovement`)は P5a で単体テストを書かない**(P4 同様、P5c の Service 結合 or integrationTest で吸収)。実装 + コンパイル確認のみ。
- **`.copy()` 禁止**(`.claude/rules/immutable-construction.md`)。不変更新は明示コンストラクタ。
- **テスト関数名は日本語**、backend JVM は Kotest `FunSpec` 可。
- 既存の Repository interface / DataSource / domain 集約は読んでから触ること(本プランの code は現行シグネチャに整合済み)。
- 有効な JAN リテラル(EAN-13 チェックディジット込み): `4901234567894`。

## File Structure

**domain(modify / 状態遷移メソッド追加):**
- `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/Household.kt` — `requireCanManage(by)` 追加
- `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/Stock.kt` — `latestMovement()` 追加
- `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/Product.kt` — `changeUnit/changeMinimum/changeImage` 追加
- tests: `domain/src/commonTest/.../household/HouseholdRequireCanManageTest.kt`、`domain/src/commonTest/.../inventory/stock/StockLatestMovementTest.kt`

**repository interface(modify)+ DataSource(modify):**
- `application/repository/product/ProductRepository.kt` + `infrastructure/datasource/product/ProductDataSource.kt` — `existsByJan` / `listWanted`
- `application/repository/stock/StockRepository.kt` + `infrastructure/datasource/stock/StockDataSource.kt` — `findByMovement`

**gateway(create):**
- `infrastructure/gateway/ExternalProductGateway.kt`(interface)
- `infrastructure/gateway/UnconfiguredProductGateway.kt`(stub 実装。実プロバイダは provider 決定後の follow-up)

**service(create)** — `application/service/<ctx>/`:
- resident: `ResidentService.kt`, `ResidentRegisterService.kt`
- household: `HouseholdService.kt`, `HouseholdRegisterService.kt`
- invitation: `InvitationService.kt`, `InvitationRegisterService.kt`
- catalog: `CatalogService.kt`
- product: `ProductService.kt`, `ProductRegisterService.kt`
- stock: `StockService.kt`, `StockRegisterService.kt`

**scenario(create)** — `application/scenario/<ctx>/`:
- product: `AdoptProductScenario.kt`
- invitation: `CreateInvitationScenario.kt`, `RevokeInvitationScenario.kt`
- household: `JoinHouseholdScenario.kt`

**tests(create)** — `backend/core/src/test/kotlin/net/brightroom/mindstock/application/...`:
- `service/catalog/CatalogServiceTest.kt`
- `service/product/ProductRegisterServiceTest.kt`
- `service/product/ProductServiceTest.kt`
- `service/stock/StockRegisterServiceTest.kt`
- `scenario/invitation/CreateInvitationScenarioTest.kt`
- `scenario/household/JoinHouseholdScenarioTest.kt`

---

## Phase 1: domain 状態遷移メソッド追加

### Task 1: `Household.requireCanManage(by)`

招待発行/失効の owner 認可を domain で判定する公開メソッド(`Household` の private `requireCapability` を世帯管理 capability で公開)。

**Files:**
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/Household.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdRequireCanManageTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.domain.model.household

import io.kotest.assertions.throwables.shouldThrow
import net.brightroom.mindstock.domain.exception.OwnerRequiredException
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import kotlin.test.Test

class HouseholdRequireCanManageTest {
    private val owner = Resident(ResidentId.create(), Profile(DisplayName("世帯主")))
    private val member = Resident(ResidentId.create(), Profile(DisplayName("メンバー")))
    private val household =
        Household.create(HouseholdName("我が家"), owner).join(member, HouseholdMemberRole.メンバー)

    @Test
    fun メンバーは世帯管理権限が無く例外() {
        shouldThrow<OwnerRequiredException> { household.requireCanManage(member.id) }
    }

    @Test
    fun 世帯主は世帯管理権限を持つ() {
        household.requireCanManage(owner.id) // 例外が出なければ合格
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:compileTestKotlinJvm`
Expected: FAIL(`requireCanManage` 未定義のコンパイルエラー)

- [ ] **Step 3: 最小実装**

`Household.kt` の `companion object` の直前(`requireCapability` private メソッドの下)に追加:

```kotlin
    /** 招待発行/失効などの世帯管理操作の認可。世帯管理 capability を持たなければ OwnerRequiredException。 */
    fun requireCanManage(by: ResidentId) {
        requireCapability(by, HouseholdCapability.世帯管理)
    }
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.household.HouseholdRequireCanManageTest"`
Expected: PASS

- [ ] **Step 5: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/Household.kt \
        domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdRequireCanManageTest.kt
git commit -m "feat(domain): Household.requireCanManage で招待系の owner 認可を公開"
```

---

### Task 2: `Stock.latestMovement()`

`replenish`/`consume`/`correct` が追記した movement を register service が取り出して永続化するためのアクセサ。

**Files:**
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/Stock.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/StockLatestMovementTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.domain.model.inventory.stock

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import kotlin.test.Test

class StockLatestMovementTest {
    private val actor = Resident(ResidentId.create(), Profile(DisplayName("たろう")))
    private val product = Product.custom(ProductName("水"), Barcode.Unlinked, ProductUnit("本"), MinimumStock(1))
    private val stock = Stock(product, StockMovements(emptyList()))

    @Test
    fun 補充後の latestMovement は今追加した補充を返す() {
        val replenished = stock.replenish(Quantity(3), OccurredAt.now(), actor, Note(""))
        val latest = replenished.latestMovement()
        latest.shouldBeInstanceOf<StockMovement.Replenishment>()
        latest.quantity() shouldBe 3
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:compileTestKotlinJvm`
Expected: FAIL(`latestMovement` 未定義)

- [ ] **Step 3: 最小実装**

`Stock.kt` の `unarchive()` の下に追加:

```kotlin
    /** 直近に追記した movement(replenish/consume/correct 後にこれを永続化する)。movement が無ければ ResourceNotFoundException。 */
    fun latestMovement(): net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement =
        movements.list.lastOrNull()
            ?: throw net.brightroom.mindstock.domain.exception.ResourceNotFoundException("no movement")
```

> import を整理して使ってよい(`StockMovement` / `ResourceNotFoundException` の import 追加 + FQCN 解消)。`ResourceNotFoundException` は既に import 済み。

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.inventory.stock.StockLatestMovementTest"`
Expected: PASS

- [ ] **Step 5: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/Stock.kt \
        domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/StockLatestMovementTest.kt
git commit -m "feat(domain): Stock.latestMovement で追記直後の movement を取り出す"
```

---

### Task 3: `Product` の変更メソッド(changeUnit / changeMinimum / changeImage)

単位・最低在庫・画像の変更は状態遷移なので domain に置く(`archive()`/`unarchive()` と同じ pattern)。単純な不変再構築のためテストは書かない(`.claude/rules/testing.md` の「値を保持するだけ」)。

**Files:**
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/Product.kt`

- [ ] **Step 1: 実装を追加**

`Product.kt` の `unarchive()`(または `archive()` の近く)に追加:

```kotlin
    fun changeUnit(unit: ProductUnit): Product =
        Product(id, name, barcode, StockingPolicy(unit, setting.minimumStock), image, status)

    fun changeMinimum(minimumStock: MinimumStock): Product =
        Product(id, name, barcode, StockingPolicy(setting.unit, minimumStock), image, status)

    fun changeImage(image: ProductImage): Product =
        Product(id, name, barcode, setting, image, status)
```

> `StockingPolicy` / `ProductUnit` / `MinimumStock` / `ProductImage` は同一集約パッケージ。未 import なら import を追加。`setting.unit` / `setting.minimumStock` は `StockingPolicy` の VO プロパティ。

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :domain:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/Product.kt
git commit -m "feat(domain): Product に changeUnit/changeMinimum/changeImage の状態遷移を追加"
```

---

## Phase 2: Repository 読み取りメソッド + DataSource 実装(単体テストは P5c/結合へ)

### Task 4: `ProductRepository.existsByJan` + DataSource 実装

重複 JAN 判定(採用中+アーカイブ済の全 product。products に tombstone は無く archive はソフト削除なので、世帯内に当該 JAN を持つ product が 1 件でもあれば true)。

**Files:**
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/product/ProductRepository.kt`
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/product/ProductDataSource.kt`

- [ ] **Step 1: interface にメソッド追加**

`ProductRepository.kt` に追加(`Jan` の import: `net.brightroom.mindstock.domain.model.barcode.Jan`):

```kotlin
    /** 世帯内に当該 JAN を持つ Product が存在するか(採用中+アーカイブ済を対象)。重複登録防止に使う。 */
    fun existsByJan(
        householdId: HouseholdId,
        jan: Jan,
    ): Boolean
```

- [ ] **Step 2: DataSource に実装追加**

`ProductDataSource.kt` の `listArchivedByHousehold` の下に追加。import に `net.brightroom.mindstock.domain.model.barcode.Jan` と `org.jetbrains.exposed.v1.jdbc.empty`(無ければ `.count()` を使う)を整える:

```kotlin
    override fun existsByJan(
        householdId: HouseholdId,
        jan: Jan,
    ): Boolean =
        transaction(database) {
            ProductsTable
                .join(
                    ProductBarcodesTable,
                    JoinType.INNER,
                    onColumn = ProductsTable.id,
                    otherColumn = ProductBarcodesTable.productId,
                ).selectAll()
                .where { (ProductsTable.householdId eq householdId()) and (ProductBarcodesTable.jan eq jan()) }
                .empty()
                .not()
        }
```

> `Query.empty()` が解決できない場合は `.count() > 0` で代替。`product_barcodes` は productId PK(product 1 件につき最大 1 行、行有無=Linked)なので JOIN + JAN 一致で判定できる。

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :backend:core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: コミット**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/product/ProductRepository.kt \
        backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/product/ProductDataSource.kt
git commit -m "feat(core): ProductRepository.existsByJan(重複JAN判定)を追加"
```

---

### Task 5: `ProductRepository.listWanted` + DataSource 実装

ShoppingList 合成用に「現在手動希望中の Product」を返す。最新 `product_wanted_events`(ROW_NUMBER Window)で `wanted = true` の product を `productRows` で hydrate。

**Files:**
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/product/ProductRepository.kt`
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/product/ProductDataSource.kt`

- [ ] **Step 1: interface にメソッド追加**

```kotlin
    /** 現在手動希望中(最新 wanted イベントが true)の Product 一覧。空なら空 Products。 */
    fun listWanted(householdId: HouseholdId): Products
```

- [ ] **Step 2: DataSource に実装追加**

import 追加: `net.brightroom.mindstock.infrastructure.datasource.schemas.ProductWantedEventsTable`、`org.jetbrains.exposed.v1.core.inList`、`kotlin.uuid.Uuid`(既に file-level OptIn 済)。

```kotlin
    override fun listWanted(householdId: HouseholdId): Products =
        transaction(database) {
            val wantedIds = latestWantedProductIds(householdId)
            if (wantedIds.isEmpty()) {
                Products(emptyList())
            } else {
                Products(
                    productRows { _ ->
                        (ProductsTable.householdId eq householdId()) and (ProductsTable.id inList wantedIds)
                    },
                )
            }
        }

    /** 世帯内で最新 wanted イベントが true の product_id 集合。 */
    private fun latestWantedProductIds(householdId: HouseholdId): Set<Uuid> {
        val rn =
            rowNumber()
                .over()
                .partitionBy(ProductWantedEventsTable.productId)
                .orderBy(ProductWantedEventsTable.id to SortOrder.DESC)
        val rnAlias = rn.alias("wrn")
        val sub =
            ProductWantedEventsTable
                .select(ProductWantedEventsTable.productId, ProductWantedEventsTable.wanted, rnAlias)
                .alias("latest_wanted")
        return ProductsTable
            .join(sub, JoinType.INNER, onColumn = ProductsTable.id, otherColumn = sub[ProductWantedEventsTable.productId])
            .selectAll()
            .where {
                (ProductsTable.householdId eq householdId()) and
                    (sub[rnAlias] eq 1L) and
                    (sub[ProductWantedEventsTable.wanted] eq true)
            }.map { it[ProductsTable.id] }
            .toSet()
    }
```

> `productRows` は同クラス内 private なので再利用可。alias 名は `productRows` 側(`latest_revision`/`rn`)と衝突しないよう `latest_wanted`/`wrn` を使用。

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :backend:core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: コミット**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/product/ProductRepository.kt \
        backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/product/ProductDataSource.kt
git commit -m "feat(core): ProductRepository.listWanted(ShoppingList 合成入力)を追加"
```

---

### Task 6: `StockRepository.findByMovement` + DataSource 実装

`correct` は productId を受けないため、MovementId から product を解決して Stock を丸ごと load する。

**Files:**
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/stock/StockRepository.kt`
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/stock/StockDataSource.kt`

- [ ] **Step 1: interface にメソッド追加**

import: `net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId`。

```kotlin
    /** 当該 movement を含む Stock を丸ごと返す(correct 用)。不在は ResourceNotFoundException。 */
    fun findByMovement(movementId: MovementId): Stock
```

- [ ] **Step 2: DataSource に実装追加**

import: `net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId`、`net.brightroom.mindstock.domain.model.inventory.product.ProductId`、`org.jetbrains.exposed.v1.jdbc.select`。

```kotlin
    override fun findByMovement(movementId: MovementId): Stock =
        transaction(database) {
            val productUuid =
                StockMovementsTable
                    .select(StockMovementsTable.productId)
                    .where { StockMovementsTable.id eq movementId() }
                    .firstOrNull()
                    ?.get(StockMovementsTable.productId)
                    ?: throw ResourceNotFoundException("movement not found: $movementId")
            val productId = ProductId(productUuid)
            Stock(productDataSource.findById(productId), loadMovements(productId))
        }
```

> `StockMovementsTable.productId` は uuid 列、`StockMovementsTable.id` は long。`movementId()` は Long を返す。`loadMovements` は同クラス private。

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :backend:core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: コミット**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/stock/StockRepository.kt \
        backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/stock/StockDataSource.kt
git commit -m "feat(core): StockRepository.findByMovement(correct の Stock 特定)を追加"
```

---

## Phase 3: ExternalProductGateway

### Task 7: Gateway interface + stub 実装

interface 抽象化(ブレスト決定)。実プロバイダ(楽天/Yahoo 等)は provider 決定後の follow-up。今は不在を返す stub を 1 実装として用意し、`CatalogService` を成立させる(lookupByJan は master 不在時に NotFound へ倒れる)。

**Files:**
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/gateway/ExternalProductGateway.kt`
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/gateway/UnconfiguredProductGateway.kt`

- [ ] **Step 1: interface を作成**

```kotlin
package net.brightroom.mindstock.infrastructure.gateway

import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem

/**
 * JAN で外部商品 API(楽天/Yahoo 等)を照会し CatalogItem を返す境界。
 * 不在 / レート制限 / 障害 / パース失敗はすべて ResourceNotFoundException に倒す
 * (理由は出し分けず、呼び出し側=CatalogService は NotFound としてフロントの手入力フォールバックへ繋ぐ)。
 */
interface ExternalProductGateway {
    fun fetch(jan: Jan): CatalogItem
}
```

- [ ] **Step 2: stub 実装を作成**

```kotlin
package net.brightroom.mindstock.infrastructure.gateway

import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem

/**
 * 外部プロバイダ未設定時の既定 Gateway。常に不在を返す。
 * 実プロバイダ(provider 決定後に <Provider>ProductGateway を実装)が用意できるまでの間、
 * lookupByJan を master 照合のみで成立させる(未存在は NotFound)。
 */
class UnconfiguredProductGateway : ExternalProductGateway {
    override fun fetch(jan: Jan): CatalogItem = throw ResourceNotFoundException("external product gateway not configured: $jan")
}
```

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :backend:core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: コミット**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/gateway/
git commit -m "feat(core): ExternalProductGateway interface + 未設定時 stub を追加"
```

---

## Phase 4: Service

### Task 8: resident services(単純委譲・テスト無し)

**Files:**
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/resident/ResidentService.kt`
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/resident/ResidentRegisterService.kt`

- [ ] **Step 1: ResidentService を作成**

```kotlin
package net.brightroom.mindstock.application.service.resident

import net.brightroom.mindstock.application.repository.resident.ResidentRepository
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

class ResidentService(
    private val residentRepository: ResidentRepository,
) {
    fun me(actor: ResidentId): Resident = residentRepository.findById(actor)
}
```

- [ ] **Step 2: ResidentRegisterService を作成**

```kotlin
package net.brightroom.mindstock.application.service.resident

import net.brightroom.mindstock.application.repository.resident.ResidentRegisterRepository
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName

class ResidentRegisterService(
    private val residentRegisterRepository: ResidentRegisterRepository,
) {
    /** UC2 初回登録。authIdentity は session 由来。Resident をここで採番して返す。 */
    fun register(
        authIdentity: AuthIdentity,
        displayName: DisplayName,
    ): Resident = residentRegisterRepository.registerResident(authIdentity, displayName)

    /** 表示名変更(append-only)。 */
    fun rename(
        actor: ResidentId,
        displayName: DisplayName,
    ) = residentRegisterRepository.appendDisplayName(actor, displayName)
}
```

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :backend:core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: コミット**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/resident/
git commit -m "feat(core): resident Service(me / register / rename)を追加"
```

---

### Task 9: household services(domain が認可を enforce・テスト無し)

`HouseholdRegisterService` は集約メソッド呼び出し→永続化。認可・最後の世帯主判定は domain が throw する(素通し)。`join` は Scenario から呼ばれる。

**Files:**
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/household/HouseholdService.kt`
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/household/HouseholdRegisterService.kt`

- [ ] **Step 1: HouseholdService を作成**

```kotlin
package net.brightroom.mindstock.application.service.household

import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.Households
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

class HouseholdService(
    private val householdRepository: HouseholdRepository,
) {
    fun list(actor: ResidentId): Households = householdRepository.listByResident(actor)

    /** 内部用(RPC では非公開)。previewInvite 組立・Scenario の owner 認可で使う。 */
    fun findById(householdId: HouseholdId): Household = householdRepository.findById(householdId)
}
```

- [ ] **Step 2: HouseholdRegisterService を作成**

```kotlin
package net.brightroom.mindstock.application.service.household

import net.brightroom.mindstock.application.repository.household.HouseholdRegisterRepository
import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.resident.ResidentRepository
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

class HouseholdRegisterService(
    private val residentRepository: ResidentRepository,
    private val householdRepository: HouseholdRepository,
    private val householdRegisterRepository: HouseholdRegisterRepository,
) {
    fun create(
        name: HouseholdName,
        actor: ResidentId,
    ): Household {
        val owner = residentRepository.findById(actor)
        val household = Household.create(name, owner)
        householdRegisterRepository.registerHousehold(household)
        return household
    }

    fun rename(
        householdId: HouseholdId,
        name: HouseholdName,
        actor: ResidentId,
    ) {
        val household = householdRepository.findById(householdId)
        household.rename(name, actor)
        householdRegisterRepository.appendHouseholdName(householdId, name)
    }

    fun leave(
        householdId: HouseholdId,
        actor: ResidentId,
    ) {
        val household = householdRepository.findById(householdId)
        household.leave(actor)
        householdRegisterRepository.removeMember(householdId, actor)
    }

    fun changeRole(
        householdId: HouseholdId,
        target: ResidentId,
        role: HouseholdMemberRole,
        actor: ResidentId,
    ) {
        val household = householdRepository.findById(householdId)
        household.changeRole(target, role, actor)
        householdRegisterRepository.changeMemberRole(householdId, target, role)
    }

    fun removeMember(
        householdId: HouseholdId,
        target: ResidentId,
        actor: ResidentId,
    ) {
        val household = householdRepository.findById(householdId)
        household.removeMember(target, actor)
        householdRegisterRepository.removeMember(householdId, target)
    }

    /** Scenario(join)用。invitation の有効性確認・actor 解決は呼び出し元(JoinHouseholdScenario)が担う。 */
    fun join(
        householdId: HouseholdId,
        resident: Resident,
        grantedRole: HouseholdMemberRole,
    ): Household {
        val household = householdRepository.findById(householdId)
        val joined = household.join(resident, grantedRole)
        householdRegisterRepository.joinMember(householdId, resident, grantedRole)
        return joined
    }
}
```

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :backend:core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: コミット**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/household/
git commit -m "feat(core): household Service(create/rename/leave/changeRole/removeMember/join/list/findById)を追加"
```

---

### Task 10: invitation services(単純委譲・テスト無し)

**Files:**
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/invitation/InvitationService.kt`
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/invitation/InvitationRegisterService.kt`

- [ ] **Step 1: InvitationService を作成**

```kotlin
package net.brightroom.mindstock.application.service.invitation

import net.brightroom.mindstock.application.repository.invitation.InvitationRepository
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode

class InvitationService(
    private val invitationRepository: InvitationRepository,
) {
    fun findByCode(code: InvitationCode): Invitation = invitationRepository.findByCode(code)
}
```

- [ ] **Step 2: InvitationRegisterService を作成**

```kotlin
package net.brightroom.mindstock.application.service.invitation

import net.brightroom.mindstock.application.repository.invitation.InvitationRegisterRepository
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode

class InvitationRegisterService(
    private val invitationRegisterRepository: InvitationRegisterRepository,
) {
    /** 発行/再発行(owner 認可・household 整合は Scenario が担う)。code PK 衝突は repo がリトライ。 */
    fun issue(invitation: Invitation): Invitation = invitationRegisterRepository.issue(invitation)

    fun revoke(code: InvitationCode) = invitationRegisterRepository.revoke(code)
}
```

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :backend:core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: コミット**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/invitation/
git commit -m "feat(core): invitation Service(findByCode / issue / revoke)を追加"
```

---

### Task 11: CatalogService(TDD・master→外部→不在の分岐)

**Files:**
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/catalog/CatalogService.kt`
- Test: `backend/core/src/test/kotlin/net/brightroom/mindstock/application/service/catalog/CatalogServiceTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.application.service.catalog

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.brightroom.mindstock.application.repository.catalog.CatalogRegisterRepository
import net.brightroom.mindstock.application.repository.catalog.CatalogRepository
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.infrastructure.gateway.ExternalProductGateway

class CatalogServiceTest : FunSpec({
    val catalogRepository = mockk<CatalogRepository>()
    val catalogRegisterRepository = mockk<CatalogRegisterRepository>(relaxed = true)
    val gateway = mockk<ExternalProductGateway>()
    val service = CatalogService(catalogRepository, catalogRegisterRepository, gateway)
    val jan = Jan("4901234567894")

    test("master にヒットしたら外部 API を呼ばない") {
        val item = CatalogItem(CatalogItemId.create(), jan, CatalogItemName("お茶"))
        every { catalogRepository.findByJan(jan) } returns item
        service.lookupByJan(jan) shouldBe item
        verify(exactly = 0) { gateway.fetch(any()) }
    }

    test("master 不在なら外部 API で取得し cache に保存して返す") {
        val fetched = CatalogItem(CatalogItemId.create(), jan, CatalogItemName("コーヒー"))
        every { catalogRepository.findByJan(jan) } throws ResourceNotFoundException("not found")
        every { gateway.fetch(jan) } returns fetched
        service.lookupByJan(jan) shouldBe fetched
        verify { catalogRegisterRepository.register(fetched) }
    }

    test("master にも外部 API にも無ければ ResourceNotFoundException を素通し") {
        every { catalogRepository.findByJan(jan) } throws ResourceNotFoundException("not found")
        every { gateway.fetch(jan) } throws ResourceNotFoundException("external miss")
        shouldThrow<ResourceNotFoundException> { service.lookupByJan(jan) }
        verify(exactly = 0) { catalogRegisterRepository.register(any()) }
    }
})
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :backend:core:compileTestKotlin`
Expected: FAIL(`CatalogService` 未定義)

- [ ] **Step 3: 実装を書く**

```kotlin
package net.brightroom.mindstock.application.service.catalog

import net.brightroom.mindstock.application.repository.catalog.CatalogRegisterRepository
import net.brightroom.mindstock.application.repository.catalog.CatalogRepository
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItems
import net.brightroom.mindstock.infrastructure.gateway.ExternalProductGateway

class CatalogService(
    private val catalogRepository: CatalogRepository,
    private val catalogRegisterRepository: CatalogRegisterRepository,
    private val externalProductGateway: ExternalProductGateway,
) {
    fun search(
        name: CatalogItemName,
        limit: Int,
    ): CatalogItems = catalogRepository.search(name, limit)

    /** 内部用(adopt の item 解決)。 */
    fun findById(catalogItemId: CatalogItemId): CatalogItem = catalogRepository.findById(catalogItemId)

    /** UC11,12: master 照合 → 未存在で外部 API → hit で cache 保存 → どちらも無ければ NotFound(素通し)。 */
    fun lookupByJan(jan: Jan): CatalogItem =
        try {
            catalogRepository.findByJan(jan)
        } catch (e: ResourceNotFoundException) {
            val fetched = externalProductGateway.fetch(jan) // 不在/失敗は ResourceNotFoundException(素通し)
            catalogRegisterRepository.register(fetched)
            fetched
        }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :backend:core:test --tests "net.brightroom.mindstock.application.service.catalog.CatalogServiceTest"`
Expected: PASS(3 件)

- [ ] **Step 5: コミット**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/catalog/ \
        backend/core/src/test/kotlin/net/brightroom/mindstock/application/service/catalog/
git commit -m "feat(core): CatalogService(search/lookupByJan の外部フォールバック)を追加"
```

---

### Task 12: ProductRegisterService(TDD・重複JAN分岐 + 変更/アーカイブ)

**Files:**
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/product/ProductRegisterService.kt`
- Test: `backend/core/src/test/kotlin/net/brightroom/mindstock/application/service/product/ProductRegisterServiceTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.application.service.product

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.brightroom.mindstock.application.repository.product.ProductRegisterRepository
import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.exception.DuplicateJanException
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit

class ProductRegisterServiceTest : FunSpec({
    val productRepository = mockk<ProductRepository>()
    val productRegisterRepository = mockk<ProductRegisterRepository>(relaxed = true)
    val stockRepository = mockk<StockRepository>()
    val service = ProductRegisterService(productRepository, productRegisterRepository, stockRepository)
    val householdId = HouseholdId.create()
    val jan = Jan("4901234567894")

    test("採用済み JAN は DuplicateJanException で採用不可") {
        val item = CatalogItem(CatalogItemId.create(), jan, CatalogItemName("お茶"))
        every { productRepository.existsByJan(householdId, jan) } returns true
        shouldThrow<DuplicateJanException> {
            service.adopt(item, householdId, ProductUnit("個"), MinimumStock(1))
        }
        verify(exactly = 0) { productRegisterRepository.registerAdopted(any(), any(), any()) }
    }

    test("未採用 JAN は採用して登録する") {
        val item = CatalogItem(CatalogItemId.create(), jan, CatalogItemName("お茶"))
        every { productRepository.existsByJan(householdId, jan) } returns false
        val product = service.adopt(item, householdId, ProductUnit("個"), MinimumStock(1))
        verify { productRegisterRepository.registerAdopted(product, householdId, item.id) }
    }

    test("addCustom は Barcode.Linked のとき重複チェックする") {
        every { productRepository.existsByJan(householdId, jan) } returns true
        shouldThrow<DuplicateJanException> {
            service.addCustom(householdId, ProductName("自作"), Barcode.Linked(jan), ProductUnit("個"), MinimumStock(0))
        }
    }

    test("addCustom は Barcode.Unlinked なら重複チェックしない") {
        val product =
            service.addCustom(householdId, ProductName("自作"), Barcode.Unlinked, ProductUnit("個"), MinimumStock(0))
        verify { productRegisterRepository.registerCustom(product, householdId) }
        verify(exactly = 0) { productRepository.existsByJan(any(), any()) }
    }
})
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :backend:core:compileTestKotlin`
Expected: FAIL(`ProductRegisterService` 未定義)

- [ ] **Step 3: 実装を書く**

```kotlin
package net.brightroom.mindstock.application.service.product

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

class ProductRegisterService(
    private val productRepository: ProductRepository,
    private val productRegisterRepository: ProductRegisterRepository,
    private val stockRepository: StockRepository,
) {
    fun adopt(
        catalogItem: CatalogItem,
        householdId: HouseholdId,
        unit: ProductUnit,
        minimumStock: MinimumStock,
    ): Product {
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
    ): Product {
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
    ) {
        val product = productRepository.findById(productId)
        productRegisterRepository.appendRevision(product.changeUnit(unit))
    }

    fun changeMinimum(
        productId: ProductId,
        minimumStock: MinimumStock,
    ) {
        val product = productRepository.findById(productId)
        productRegisterRepository.appendRevision(product.changeMinimum(minimumStock))
    }

    fun changeImage(
        productId: ProductId,
        image: ProductImage,
    ) {
        val product = productRepository.findById(productId)
        productRegisterRepository.appendRevision(product.changeImage(image))
    }

    /** 在庫 0 のときのみ可。ガードは Stock.archive() が担保する。 */
    fun archive(productId: ProductId) {
        val stock = stockRepository.findByProduct(productId)
        productRegisterRepository.appendRevision(stock.archive().product)
    }

    fun unarchive(productId: ProductId) {
        val stock = stockRepository.findByProduct(productId)
        productRegisterRepository.appendRevision(stock.unarchive().product)
    }

    fun setWanted(
        productId: ProductId,
        wanted: Boolean,
    ) = productRegisterRepository.setWanted(productId, wanted)
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :backend:core:test --tests "net.brightroom.mindstock.application.service.product.ProductRegisterServiceTest"`
Expected: PASS(4 件)

- [ ] **Step 5: コミット**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/product/ProductRegisterService.kt \
        backend/core/src/test/kotlin/net/brightroom/mindstock/application/service/product/ProductRegisterServiceTest.kt
git commit -m "feat(core): ProductRegisterService(adopt/addCustom の重複JAN・変更・アーカイブ)を追加"
```

---

### Task 13: ProductService(TDD・ShoppingList 合成)

**Files:**
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/product/ProductService.kt`
- Test: `backend/core/src/test/kotlin/net/brightroom/mindstock/application/service/product/ProductServiceTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.application.service.product

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.Products
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements

class ProductServiceTest : FunSpec({
    val stockRepository = mockk<StockRepository>()
    val productRepository = mockk<ProductRepository>()
    val service = ProductService(stockRepository, productRepository)
    val householdId = HouseholdId.create()

    test("shoppingList は手動希望フラグを Stock に突き合わせて合成する") {
        val wanted = Product.custom(ProductName("水"), Barcode.Unlinked, ProductUnit("本"), MinimumStock(5))
        val other = Product.custom(ProductName("米"), Barcode.Unlinked, ProductUnit("袋"), MinimumStock(1))
        every { stockRepository.listByHousehold(householdId) } returns
            Stocks(listOf(Stock(wanted, StockMovements(emptyList())), Stock(other, StockMovements(emptyList()))))
        every { productRepository.listWanted(householdId) } returns Products(listOf(wanted))

        val list = service.shoppingList(householdId)

        list.list.first { it.stock.product.id == wanted.id }.manuallyWanted shouldBe true
        list.list.first { it.stock.product.id == other.id }.manuallyWanted shouldBe false
    }
})
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :backend:core:compileTestKotlin`
Expected: FAIL(`ProductService` 未定義)

- [ ] **Step 3: 実装を書く**

```kotlin
package net.brightroom.mindstock.application.service.product

import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Products
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingEntry
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingList
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks

class ProductService(
    private val stockRepository: StockRepository,
    private val productRepository: ProductRepository,
) {
    /** 在庫一覧(数量+status を見せるため Stock 集合)。 */
    fun list(householdId: HouseholdId): Stocks = stockRepository.listByHousehold(householdId)

    fun listArchived(householdId: HouseholdId): Products = productRepository.listArchivedByHousehold(householdId)

    /** 買い物リスト(自動=在庫不足 + 手動希望)。Stock 集合 × 手動希望 を read-model に合成する。 */
    fun shoppingList(householdId: HouseholdId): ShoppingList {
        val stocks = stockRepository.listByHousehold(householdId)
        val wantedIds = productRepository.listWanted(householdId).list.map { it.id }.toSet()
        return ShoppingList(stocks.list.map { ShoppingEntry(it, it.product.id in wantedIds) })
    }
}
```

> `ShoppingEntry` / `ShoppingList` の package は `domain.model.inventory.shopping`。`ShoppingEntry(stock, manuallyWanted)` のフィールド名は spec の domain map 準拠(`stock` / `manuallyWanted`)。実コードでフィールド名が異なる場合は domain に合わせる。

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :backend:core:test --tests "net.brightroom.mindstock.application.service.product.ProductServiceTest"`
Expected: PASS

- [ ] **Step 5: コミット**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/product/ProductService.kt \
        backend/core/src/test/kotlin/net/brightroom/mindstock/application/service/product/ProductServiceTest.kt
git commit -m "feat(core): ProductService(list/listArchived/shoppingList 合成)を追加"
```

---

### Task 14: stock services(StockService 委譲 + StockRegisterService。correct のみ TDD)

**Files:**
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/stock/StockService.kt`
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/stock/StockRegisterService.kt`
- Test: `backend/core/src/test/kotlin/net/brightroom/mindstock/application/service/stock/StockRegisterServiceTest.kt`

- [ ] **Step 1: StockService(参照系・テスト無し)を作成**

```kotlin
package net.brightroom.mindstock.application.service.stock

import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements

class StockService(
    private val stockRepository: StockRepository,
) {
    fun history(productId: ProductId): StockMovements = stockRepository.historyOf(productId)

    /** 世帯全体の活動履歴。Controller(P5c)が ActivityFeed に flatten する。 */
    fun activity(householdId: HouseholdId): Stocks = stockRepository.listByHousehold(householdId)
}
```

- [ ] **Step 2: 失敗するテスト(correct の Stock 特定)を書く**

```kotlin
package net.brightroom.mindstock.application.service.stock

import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.brightroom.mindstock.application.repository.resident.ResidentRepository
import net.brightroom.mindstock.application.repository.stock.StockRegisterRepository
import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementIdentity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Reason
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile

class StockRegisterServiceTest : FunSpec({
    val residentRepository = mockk<ResidentRepository>()
    val stockRepository = mockk<StockRepository>()
    val stockRegisterRepository = mockk<StockRegisterRepository>(relaxed = true)
    val service = StockRegisterService(residentRepository, stockRepository, stockRegisterRepository)

    val actor = Resident(ResidentId.create(), Profile(DisplayName("たろう")))
    val product = Product.custom(ProductName("水"), Barcode.Unlinked, ProductUnit("本"), MinimumStock(1))

    test("correct は findByMovement で対象を load し訂正 movement を append する") {
        val baseId = MovementId(1L)
        val base =
            StockMovement.Replenishment(
                MovementIdentity.Persisted(baseId), Quantity(5), OccurredAt.now(), actor, Note(""),
            )
        every { residentRepository.findById(actor.id) } returns actor
        every { stockRepository.findByMovement(baseId) } returns Stock(product, StockMovements(listOf(base)))

        val appended = slot<StockMovement>()
        every { stockRegisterRepository.appendMovement(product.id, capture(appended)) } returns base

        service.correct(baseId, Quantity(3), Reason("数え間違い"), actor.id)

        verify { stockRepository.findByMovement(baseId) }
        check(appended.captured is StockMovement.Correction) { "appended movement must be a Correction" }
    }
})
```

- [ ] **Step 3: テストが失敗することを確認**

Run: `./gradlew :backend:core:compileTestKotlin`
Expected: FAIL(`StockRegisterService` 未定義)

- [ ] **Step 4: StockRegisterService を実装**

```kotlin
package net.brightroom.mindstock.application.service.stock

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
) {
    fun replenish(
        productId: ProductId,
        quantity: Quantity,
        note: Note,
        actor: ResidentId,
    ) {
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
        val resident = residentRepository.findById(actor)
        val stock = stockRepository.findByMovement(target)
        val corrected = stock.correct(target, correctedQuantity, reason, resident, OccurredAt.now())
        stockRegisterRepository.appendMovement(stock.product.id, corrected.latestMovement())
    }
}
```

- [ ] **Step 5: テストが通ることを確認**

Run: `./gradlew :backend:core:test --tests "net.brightroom.mindstock.application.service.stock.StockRegisterServiceTest"`
Expected: PASS

- [ ] **Step 6: コミット**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/stock/ \
        backend/core/src/test/kotlin/net/brightroom/mindstock/application/service/stock/
git commit -m "feat(core): stock Service(history/activity + replenish/consume/correct)を追加"
```

---

## Phase 5: Scenario

### Task 15: AdoptProductScenario(委譲・テスト無し)

catalog の item 解決 → product の採用(重複チェックは ProductRegisterService 内)。単純委譲のためテストは書かない(`adopt` の重複分岐は Task 12 で検証済み)。

**Files:**
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/scenario/product/AdoptProductScenario.kt`

- [ ] **Step 1: 実装を書く**

```kotlin
package net.brightroom.mindstock.application.scenario.product

import net.brightroom.mindstock.application.service.catalog.CatalogService
import net.brightroom.mindstock.application.service.product.ProductRegisterService
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit

class AdoptProductScenario(
    private val catalogService: CatalogService,
    private val productRegisterService: ProductRegisterService,
) {
    fun run(
        householdId: HouseholdId,
        catalogItemId: CatalogItemId,
        unit: ProductUnit,
        minimumStock: MinimumStock,
    ): Product {
        val item = catalogService.findById(catalogItemId)
        return productRegisterService.adopt(item, householdId, unit, minimumStock)
    }
}
```

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :backend:core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: コミット**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/application/scenario/product/
git commit -m "feat(core): AdoptProductScenario(catalog 解決→採用)を追加"
```

---

### Task 16: CreateInvitationScenario + RevokeInvitationScenario(TDD・owner 認可)

`RevokeInvitationScenario` は `CreateInvitationScenario` と同型の owner 認可(invitation を解決して household の owner を確認)。owner 認可分岐は CreateInvitation のテストで代表検証し、Revoke のテストは機械的重複のため省略する(`.claude/rules/testing.md`)。

**Files:**
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/scenario/invitation/CreateInvitationScenario.kt`
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/scenario/invitation/RevokeInvitationScenario.kt`
- Test: `backend/core/src/test/kotlin/net/brightroom/mindstock/application/scenario/invitation/CreateInvitationScenarioTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.application.scenario.invitation

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.invitation.InvitationRegisterService
import net.brightroom.mindstock.domain.exception.OwnerRequiredException
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile

class CreateInvitationScenarioTest : FunSpec({
    val householdService = mockk<HouseholdService>()
    val invitationRegisterService = mockk<InvitationRegisterService>(relaxed = true)
    val scenario = CreateInvitationScenario(householdService, invitationRegisterService)

    val owner = Resident(ResidentId.create(), Profile(DisplayName("世帯主")))
    val member = Resident(ResidentId.create(), Profile(DisplayName("メンバー")))
    val household = Household.create(HouseholdName("我が家"), owner).join(member, HouseholdMemberRole.メンバー)

    test("メンバーは招待を発行できず OwnerRequiredException(issue を呼ばない)") {
        every { householdService.findById(household.id) } returns household
        shouldThrow<OwnerRequiredException> {
            scenario.run(household.id, HouseholdMemberRole.メンバー, member.id)
        }
        verify(exactly = 0) { invitationRegisterService.issue(any()) }
    }

    test("世帯主は招待を発行できる") {
        every { householdService.findById(household.id) } returns household
        val issued = Invitation.issue(household.id, HouseholdMemberRole.メンバー)
        every { invitationRegisterService.issue(any()) } returns issued
        scenario.run(household.id, HouseholdMemberRole.メンバー, owner.id) shouldBe issued
    }
})
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :backend:core:compileTestKotlin`
Expected: FAIL(`CreateInvitationScenario` 未定義)

- [ ] **Step 3: 両 Scenario を実装**

`CreateInvitationScenario.kt`:

```kotlin
package net.brightroom.mindstock.application.scenario.invitation

import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.invitation.InvitationRegisterService
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

class CreateInvitationScenario(
    private val householdService: HouseholdService,
    private val invitationRegisterService: InvitationRegisterService,
) {
    fun run(
        householdId: HouseholdId,
        role: HouseholdMemberRole,
        actor: ResidentId,
    ): Invitation {
        householdService.findById(householdId).requireCanManage(actor)
        return invitationRegisterService.issue(Invitation.issue(householdId, role))
    }
}
```

`RevokeInvitationScenario.kt`:

```kotlin
package net.brightroom.mindstock.application.scenario.invitation

import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.invitation.InvitationRegisterService
import net.brightroom.mindstock.application.service.invitation.InvitationService
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

class RevokeInvitationScenario(
    private val invitationService: InvitationService,
    private val householdService: HouseholdService,
    private val invitationRegisterService: InvitationRegisterService,
) {
    fun run(
        code: InvitationCode,
        actor: ResidentId,
    ) {
        val invitation = invitationService.findByCode(code)
        householdService.findById(invitation.householdId).requireCanManage(actor)
        invitationRegisterService.revoke(code)
    }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :backend:core:test --tests "net.brightroom.mindstock.application.scenario.invitation.CreateInvitationScenarioTest"`
Expected: PASS(2 件)

- [ ] **Step 5: コミット**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/application/scenario/invitation/ \
        backend/core/src/test/kotlin/net/brightroom/mindstock/application/scenario/invitation/
git commit -m "feat(core): Create/RevokeInvitationScenario(owner 認可→発行/失効)を追加"
```

---

### Task 17: JoinHouseholdScenario(TDD・有効性ガード)

**Files:**
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/scenario/household/JoinHouseholdScenario.kt`
- Test: `backend/core/src/test/kotlin/net/brightroom/mindstock/application/scenario/household/JoinHouseholdScenarioTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.application.scenario.household

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.brightroom.mindstock.application.service.household.HouseholdRegisterService
import net.brightroom.mindstock.application.service.invitation.InvitationService
import net.brightroom.mindstock.application.service.resident.ResidentService
import net.brightroom.mindstock.domain.exception.InvitationInvalidException
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.domain.model.household.invitation.InvitationValidity
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile

class JoinHouseholdScenarioTest : FunSpec({
    val invitationService = mockk<InvitationService>()
    val residentService = mockk<ResidentService>()
    val householdRegisterService = mockk<HouseholdRegisterService>(relaxed = true)
    val scenario = JoinHouseholdScenario(invitationService, residentService, householdRegisterService)

    val code = InvitationCode("ABCDEF")
    val joiner = Resident(ResidentId.create(), Profile(DisplayName("参加者")))
    val householdId = HouseholdId.create()

    test("無効な招待コードでは参加できず InvitationInvalidException(join を呼ばない)") {
        every { invitationService.findByCode(code) } returns
            Invitation(householdId, code, HouseholdMemberRole.メンバー, InvitationValidity.無効)
        shouldThrow<InvitationInvalidException> { scenario.run(code, joiner.id) }
        verify(exactly = 0) { householdRegisterService.join(any(), any(), any()) }
    }

    test("有効な招待コードで世帯に参加する") {
        every { invitationService.findByCode(code) } returns
            Invitation(householdId, code, HouseholdMemberRole.メンバー, InvitationValidity.有効)
        every { residentService.me(joiner.id) } returns joiner
        val joined = Household.create(HouseholdName("我が家"), joiner)
        every { householdRegisterService.join(householdId, joiner, HouseholdMemberRole.メンバー) } returns joined
        scenario.run(code, joiner.id) shouldBe joined
    }
})
```

> `InvitationValidity` の entry 名(`有効`/`無効`)は domain 実装に合わせる(`Invitation.kt` 参照)。

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :backend:core:compileTestKotlin`
Expected: FAIL(`JoinHouseholdScenario` 未定義)

- [ ] **Step 3: 実装を書く**

```kotlin
package net.brightroom.mindstock.application.scenario.household

import net.brightroom.mindstock.application.service.household.HouseholdRegisterService
import net.brightroom.mindstock.application.service.invitation.InvitationService
import net.brightroom.mindstock.application.service.resident.ResidentService
import net.brightroom.mindstock.domain.exception.InvitationInvalidException
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

class JoinHouseholdScenario(
    private val invitationService: InvitationService,
    private val residentService: ResidentService,
    private val householdRegisterService: HouseholdRegisterService,
) {
    fun run(
        code: InvitationCode,
        actor: ResidentId,
    ): Household {
        val invitation = invitationService.findByCode(code)
        if (!invitation.usable()) {
            throw InvitationInvalidException("invitation not usable: $code")
        }
        val resident = residentService.me(actor)
        return householdRegisterService.join(invitation.householdId, resident, invitation.grantedRole)
    }
}
```

> `InvitationInvalidException` が domain に未定義の場合は `domain/src/commonMain/.../exception/InvitationInvalidException.kt` を `class InvitationInvalidException(reason: String) : RuntimeException(reason)` で追加してから実装する(error-handling ルールの「前提崩れ専用例外」)。

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :backend:core:test --tests "net.brightroom.mindstock.application.scenario.household.JoinHouseholdScenarioTest"`
Expected: PASS(2 件)

- [ ] **Step 5: コミット**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/application/scenario/household/ \
        backend/core/src/test/kotlin/net/brightroom/mindstock/application/scenario/household/
git commit -m "feat(core): JoinHouseholdScenario(招待有効性→参加)を追加"
```

---

## Phase 6: 仕上げ

### Task 18: 層依存・全体ビルド確認

- [ ] **Step 1: application/infrastructure が :rpc / MindstockSession に依存していないことを確認**

Run: `grep -rn "rpc.result\|RpcError\|RpcResult\|MindstockSession" backend/core/src/main`
Expected: 出力なし(0 件)

- [ ] **Step 2: :domain と :backend:core のフルビルド(ユニットテスト含む)**

Run: `./gradlew :domain:build :backend:core:build`
Expected: BUILD SUCCESSFUL(integrationTest は外部 DB が要るため別途。ここでは通常 test のみ)

> ローカルビルド留意([[local-build-tips]]): frontend WasmJs は OOM るので含めない。`:backend:core:build` は testcontainers を起動しない(本 P5a は infra 単体テストを書かないため)。

- [ ] **Step 3: 受け入れ条件チェック(spec §受け入れ条件)**

- [ ] 全 `@Rpc` 契約メソッドに対応する application 入口(Service or Scenario)が存在する
  - resident: `me`/`registerDisplayName(register)`/`rename`、household: `create/rename/leave/changeRole/removeMember/list` + `CreateInvitation/RevokeInvitation/JoinHousehold` Scenario + `previewInvite` 用 `findByCode`/`findById`、catalog: `search/lookupByJan`、product: `list/listArchived/shoppingList` + `AdoptProduct` Scenario + `addCustom/changeUnit/changeImage/changeMinimum/archive/unarchive/setWanted`、stock: `history/activity` + `replenish/consume/correct`
- [ ] `ExternalProductGateway` interface + 1 実装(stub)が DI 可能な形で存在する

- [ ] **Step 4: コミット(必要なら)**

ここまでで未コミットの調整があればまとめてコミット:

```bash
git status
git commit -am "chore(core): P5a 受け入れ確認(層依存・ビルド緑)" || true
```

---

## 後続(P5a の外・provider 決定後)

- **実 ExternalProductGateway 実装**: provider(楽天市場商品検索 / Yahoo!ショッピング等)決定後に `<Provider>ProductGateway` を実装。Ktor client(cio)+ ContentNegotiation を `:backend:core` `build.gradle.kts` に追加し、config 駆動(base URL / application-id)、timeout / レート制限 / パース失敗を `ResourceNotFoundException` に集約。DI で `UnconfiguredProductGateway` と差し替える。
- **P5b(認証)** / **P5c(presentation + 起動配線)**: 別 spec / 別プラン。

## Self-Review(記入済み)

- **Spec coverage**: spec の Service/Scenario インベントリ・確定設計判断・repo/domain 追加・gateway・テスト方針を Task 1–18 が網羅。`previewInvite`/`ActivityFeed`/`AddCustomProductRequest` は P5c 担当(spec 決定 #5/#6)なので P5a の対象外で正しい。
- **Placeholder scan**: 各 code step に実コードを記載。`UnconfiguredProductGateway` は意図的な stub(provider 未決定のため)で TODO ではない。
- **Type consistency**: `existsByJan(householdId, jan)` / `listWanted(householdId): Products` / `findByMovement(movementId): Stock` / `requireCanManage(by)` / `latestMovement()` / `changeUnit/changeMinimum/changeImage` は定義タスクと利用タスクで一致。`ShoppingEntry(stock, manuallyWanted)` / `InvitationValidity` entry 名は domain 実装に合わせる旨を明記(実装時に確認)。
