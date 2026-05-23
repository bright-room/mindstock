# Stock Movements 統一化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stock 関連の 4 テーブル(`stock_replenishments` / `stock_consumptions` / `stock_replenishment_corrections` / `stock_consumption_corrections`)を `stock_movements` 1 本に統合し、訂正概念を廃止する。

**Architecture:** sealed interface `StockMovement` を `Replenishment` / `Consumption` 2 subclass で実装。`Stock.currentQuantity()` は `StockMovements.netQuantity()`(補充 + / 消費 −)に簡素化。訂正は別概念ではなく単なる movement 追加で表現するため、`*Correction` / `EffectiveQuantity` / `Reason` / `CorrectedAt` / `*Id` 系を全削除。

**Tech Stack:** Kotlin (commonMain, KMP) / Exposed v1 / PostgreSQL 18 / Flyway / Kotest / Testcontainers

**Spec:** [`docs/superpowers/specs/2026-05-24-stock-movements-unification-design.md`](../specs/2026-05-24-stock-movements-unification-design.md)

---

## File Structure

### Created
- `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/movement/StockMovementType.kt` — enum `REPLENISHMENT` / `CONSUMPTION`
- `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/movement/StockMovement.kt` — sealed interface
- `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/movement/Replenishment.kt` — data class : StockMovement
- `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/movement/Consumption.kt` — data class : StockMovement
- `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/movement/StockMovements.kt` — collection with `netQuantity()`
- `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/movement/StockMovementsTest.kt` — netQuantity tests
- `backend/infrastructure/schemas/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/schemas/stock/StockMovementsTable.kt` — Exposed Table

### Modified
- `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/Stock.kt` — `movements: StockMovements` ベースに書き換え
- `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/repository/stock/StockRegisterRepository.kt` — 訂正系メソッド削除、戻り値を Unit に
- `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/repository/stock/StockRepository.kt` — `replenishmentHistory` / `consumptionHistory` → `movementHistory` に統合
- `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/StockTest.kt` — 全面書き直し
- `backend/infrastructure/migration/detector/src/main/kotlin/net/brightroom/mindstock/infrastructure/migration/detector/MigratableTables.kt` — 旧 4 Table 削除、`StockMovementsTable` 追加
- `backend/infrastructure/migration/executor/src/main/resources/db/migration/V20260523071825__init.sql` — 再生成
- `docs/superpowers/specs/2026-05-23-mindstock-design.md` — §4.3 / §4.5 / §5.5 / §6.1 を本仕様で置き換え(末尾に supersession 注記追加)

### Deleted
- `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/replenishment/Replenishment.kt`
- `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/replenishment/ReplenishmentId.kt`
- `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/replenishment/Replenishments.kt`
- `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/replenishment/ReplenishmentCorrection.kt`
- `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/replenishment/ReplenishmentCorrections.kt`
- `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/consumption/Consumption.kt`
- `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/consumption/ConsumptionId.kt`
- `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/consumption/Consumptions.kt`
- `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/consumption/ConsumptionCorrection.kt`
- `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/consumption/ConsumptionCorrections.kt`
- `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/EffectiveQuantity.kt`
- `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/CorrectedAt.kt`
- `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/Reason.kt`
- `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/CorrectedAtTest.kt`
- `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/ReasonTest.kt`
- `backend/infrastructure/schemas/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/schemas/stock/StockReplenishmentsTable.kt`
- `backend/infrastructure/schemas/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/schemas/stock/StockConsumptionsTable.kt`
- `backend/infrastructure/schemas/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/schemas/stock/StockReplenishmentCorrectionsTable.kt`
- `backend/infrastructure/schemas/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/schemas/stock/StockConsumptionCorrectionsTable.kt`

---

## Task 1: 新 Domain クラス群を追加(失敗テスト先行)

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/movement/StockMovementType.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/movement/StockMovement.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/movement/Replenishment.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/movement/Consumption.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/movement/StockMovements.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/movement/StockMovementsTest.kt`

- [ ] **Step 1: 失敗テストを書く**

`domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/movement/StockMovementsTest.kt`:

```kotlin
package net.brightroom.mindstock.domain.model.stock.movement

import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import kotlin.test.Test
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class StockMovementsTest {
    private val now = Instant.parse("2026-05-24T10:00:00Z")
    private val user =
        User(
            id = UserId(Uuid.generateV7()),
            authIdentity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-1")),
            displayName = DisplayName("alice"),
        )
    private val product =
        Product(
            id = ProductId(Uuid.generateV7()),
            catalogItem =
                CatalogItem(
                    id = CatalogItemId(Uuid.generateV7()),
                    name = CatalogItemName("ハンドソープ"),
                    unit = CatalogItemUnit("本"),
                ),
            minimumStock = null,
            archived = false,
        )

    private fun occurred() =
        OccurredAt(
            LocalDateTime(2026, 5, 1, 10, 0).toInstant(TimeZone.UTC),
            now,
        )

    private fun replenish(qty: Int) =
        Replenishment(product, Quantity(qty), occurred(), user, Note(""))

    private fun consume(qty: Int) =
        Consumption(product, Quantity(qty), occurred(), user, Note(""))

    @Test
    fun `netQuantity is zero for empty movements`() {
        StockMovements(emptyList()).netQuantity() shouldBe 0
    }

    @Test
    fun `netQuantity sums replenishments as positive`() {
        StockMovements(listOf(replenish(5), replenish(3))).netQuantity() shouldBe 8
    }

    @Test
    fun `netQuantity sums consumptions as negative`() {
        StockMovements(listOf(consume(2), consume(1))).netQuantity() shouldBe -3
    }

    @Test
    fun `netQuantity mixes replenishments and consumptions`() {
        StockMovements(listOf(replenish(10), consume(3), replenish(2), consume(4)))
            .netQuantity() shouldBe 5
    }
}
```

- [ ] **Step 2: テストが compile error で失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "*StockMovementsTest*"`
Expected: コンパイル失敗(`Replenishment` / `Consumption` / `StockMovements` 未定義)

- [ ] **Step 3: `StockMovementType` を作成**

`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/movement/StockMovementType.kt`:

```kotlin
package net.brightroom.mindstock.domain.model.stock.movement

/**
 * 在庫変動の種別。
 *
 * - [REPLENISHMENT]: 在庫を増やす事実(補充)
 * - [CONSUMPTION]: 在庫を減らす事実(消費)
 *
 * 「補充の誤りを訂正する」操作は別 type ではなく、単に逆方向の movement を 1 件追加することで表現する。
 */
enum class StockMovementType {
    REPLENISHMENT,
    CONSUMPTION,
}
```

- [ ] **Step 4: `StockMovement` sealed interface を作成**

`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/movement/StockMovement.kt`:

```kotlin
package net.brightroom.mindstock.domain.model.stock.movement

import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.user.User

/**
 * 在庫変動の事実(append-only)。
 *
 * id は持たない(domain 上で参照する操作がない。BIGSERIAL は DB の関心事)。
 */
sealed interface StockMovement {
    val product: Product
    val quantity: Quantity
    val occurredAt: OccurredAt
    val actor: User
    val note: Note
    val type: StockMovementType
}
```

- [ ] **Step 5: `Replenishment` data class を作成**

`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/movement/Replenishment.kt`:

```kotlin
package net.brightroom.mindstock.domain.model.stock.movement

import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.user.User

data class Replenishment(
    override val product: Product,
    override val quantity: Quantity,
    override val occurredAt: OccurredAt,
    override val actor: User,
    override val note: Note,
) : StockMovement {
    override val type: StockMovementType get() = StockMovementType.REPLENISHMENT
}
```

- [ ] **Step 6: `Consumption` data class を作成**

`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/movement/Consumption.kt`:

```kotlin
package net.brightroom.mindstock.domain.model.stock.movement

import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.user.User

data class Consumption(
    override val product: Product,
    override val quantity: Quantity,
    override val occurredAt: OccurredAt,
    override val actor: User,
    override val note: Note,
) : StockMovement {
    override val type: StockMovementType get() = StockMovementType.CONSUMPTION
}
```

- [ ] **Step 7: `StockMovements` collection を作成**

`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/movement/StockMovements.kt`:

```kotlin
package net.brightroom.mindstock.domain.model.stock.movement

/**
 * StockMovement のコレクション。
 *
 * netQuantity は補充を正・消費を負として全 movement を線形集計した正味数量。
 * `Stock.currentQuantity()` はこれをそのまま使う。
 */
class StockMovements(
    private val list: List<StockMovement>,
) {
    fun asList(): List<StockMovement> = list.toList()

    val size: Int get() = list.size

    fun netQuantity(): Int =
        list.sumOf { m ->
            when (m) {
                is Replenishment -> +m.quantity.invoke()
                is Consumption -> -m.quantity.invoke()
            }
        }
}
```

注: `Quantity` の内部値取得は `internal operator fun invoke(): Int` で実装されているため、同モジュール内の `StockMovements` からは `m.quantity()` ではなく `m.quantity.invoke()` を呼べる(Kotlin の lint で operator 呼び出しに直してもよい)。コンパイルが通る形式を採用すること。

- [ ] **Step 8: テスト実行**

Run: `./gradlew :domain:jvmTest --tests "*StockMovementsTest*"`
Expected: 4 件 PASS

- [ ] **Step 9: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/movement \
        domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/movement
git commit -m "feat(domain): add StockMovement sealed interface and StockMovements"
```

---

## Task 2: `Stock` を `StockMovements` ベースに書き換え

**Files:**
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/Stock.kt`
- Modify: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/StockTest.kt`

- [ ] **Step 1: `StockTest` を新モデル前提に全面書き直し**

`domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/StockTest.kt` を以下に置き換え(訂正系テストは削除、混在シナリオで「訂正は単に消費追加」を表現):

```kotlin
package net.brightroom.mindstock.domain.model.stock

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.stock.movement.Consumption
import net.brightroom.mindstock.domain.model.stock.movement.Replenishment
import net.brightroom.mindstock.domain.model.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import kotlin.test.Test
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class StockTest {
    private val user =
        User(
            id = UserId(Uuid.generateV7()),
            authIdentity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-1")),
            displayName = DisplayName("alice"),
        )

    private fun productWithMin(min: Int?) =
        Product(
            id = ProductId(Uuid.generateV7()),
            catalogItem =
                CatalogItem(
                    id = CatalogItemId(Uuid.generateV7()),
                    name = CatalogItemName("ハンドソープ"),
                    unit = CatalogItemUnit("本"),
                ),
            minimumStock = min?.let { MinimumStock(it) },
            archived = false,
        )

    private val now = Instant.parse("2026-05-24T10:00:00Z")

    private fun occurred() =
        OccurredAt(
            LocalDateTime(2026, 5, 1, 10, 0).toInstant(TimeZone.UTC),
            now,
        )

    private fun replenish(
        product: Product,
        qty: Int,
    ) = Replenishment(product, Quantity(qty), occurred(), user, Note(""))

    private fun consume(
        product: Product,
        qty: Int,
    ) = Consumption(product, Quantity(qty), occurred(), user, Note(""))

    @Test
    fun `currentQuantity is sum of replenishments minus consumptions`() {
        val p = productWithMin(null)
        val stock =
            Stock(
                product = p,
                movements = StockMovements(listOf(replenish(p, 5), replenish(p, 3), consume(p, 2))),
            )
        stock.currentQuantity() shouldBe 6
    }

    @Test
    fun `currentQuantity is zero when no movements`() {
        val p = productWithMin(null)
        Stock(p, StockMovements(emptyList())).currentQuantity() shouldBe 0
    }

    @Test
    fun `needsReplenishment is true when current quantity is below minimum`() {
        val p = productWithMin(5)
        val stock = Stock(p, StockMovements(listOf(replenish(p, 3))))
        stock.needsReplenishment().shouldBeTrue()
        stock.shortage() shouldBe 2
    }

    @Test
    fun `needsReplenishment is false when minimumStock is null`() {
        val p = productWithMin(null)
        val stock = Stock(p, StockMovements(emptyList()))
        stock.needsReplenishment().shouldBeFalse()
    }

    @Test
    fun `correction is expressed as an additional consumption movement`() {
        // ユーザーが 3 個補充したつもりが 2 個だった場合、訂正用 API は無く、
        // 単に消費 1 を追加することで在庫の整合性を取る(訂正概念廃止の意図表現)。
        val p = productWithMin(null)
        val stock =
            Stock(
                product = p,
                movements = StockMovements(listOf(replenish(p, 3), consume(p, 1))),
            )
        stock.currentQuantity() shouldBe 2
    }
}
```

- [ ] **Step 2: 既存テスト + 新テストを実行して旧 `Stock` の API で失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "*StockTest"`
Expected: コンパイル失敗(`Stock` のシグネチャが旧)

- [ ] **Step 3: `Stock` を書き換え**

`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/Stock.kt` を以下で全置換:

```kotlin
package net.brightroom.mindstock.domain.model.stock

import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.movement.StockMovements

/**
 * 在庫状態。
 *
 * 1 つの Product に対する全 movement (補充・消費) から現在数量・買い物リスト要否を計算する。
 * 訂正は別概念ではなく、単に逆方向の movement を 1 件追加することで表現する。
 */
class Stock(
    val product: Product,
    val movements: StockMovements,
) {
    fun currentQuantity(): Int = movements.netQuantity()

    fun needsReplenishment(): Boolean {
        val minimum = product.minimumStock?.let { it() } ?: return false
        return currentQuantity() < minimum
    }

    fun shortage(): Int {
        val minimum = product.minimumStock?.let { it() } ?: 0
        return (minimum - currentQuantity()).coerceAtLeast(0)
    }
}
```

- [ ] **Step 4: テスト実行**

Run: `./gradlew :domain:jvmTest --tests "*StockTest"`
Expected: 5 件 PASS

- [ ] **Step 5: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/Stock.kt \
        domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/StockTest.kt
git commit -m "refactor(domain): rewrite Stock on StockMovements, drop corrections"
```

---

## Task 3: Repository インターフェースを更新

**Files:**
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/repository/stock/StockRegisterRepository.kt`
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/repository/stock/StockRepository.kt`

- [ ] **Step 1: `StockRegisterRepository` を書き換え**

`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/repository/stock/StockRegisterRepository.kt` 全置換:

```kotlin
package net.brightroom.mindstock.domain.repository.stock

import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.movement.Consumption
import net.brightroom.mindstock.domain.model.stock.movement.Replenishment
import net.brightroom.mindstock.domain.model.user.User

interface StockRegisterRepository {
    fun replenish(
        product: Product,
        quantity: Quantity,
        occurredAt: OccurredAt,
        by: User,
        note: Note,
    ): Replenishment

    fun consume(
        product: Product,
        quantity: Quantity,
        occurredAt: OccurredAt,
        by: User,
        note: Note,
    ): Consumption
}
```

- [ ] **Step 2: `StockRepository` を書き換え**

`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/repository/stock/StockRepository.kt` 全置換:

```kotlin
package net.brightroom.mindstock.domain.repository.stock

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Stock
import net.brightroom.mindstock.domain.model.stock.movement.StockMovements

interface StockRepository {
    /** 1 商品の在庫状態。 */
    fun stockOf(product: Product): Stock

    /** 世帯全商品の在庫状態(ShoppingList 用)。 */
    fun stocksOf(household: Household): List<Stock>

    /** 指定商品の movement 履歴(最新順を想定)。 */
    fun movementHistory(
        product: Product,
        limit: Int = 50,
    ): StockMovements
}
```

- [ ] **Step 3: ビルド確認(まだ旧 `Replenishment` 等が残っているため失敗するはず)**

Run: `./gradlew :domain:compileKotlinJvm`
Expected: コンパイル失敗(旧 `replenishment.Replenishment` を import している箇所が残存)— 次タスクで除去

- [ ] **Step 4: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/repository/stock
git commit -m "refactor(domain): simplify Stock repositories, drop correction methods"
```

---

## Task 4: 旧 Domain クラスを全削除

**Files (Delete):**
- `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/replenishment/` (5 files: Replenishment, ReplenishmentId, Replenishments, ReplenishmentCorrection, ReplenishmentCorrections)
- `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/consumption/` (5 files: Consumption, ConsumptionId, Consumptions, ConsumptionCorrection, ConsumptionCorrections)
- `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/EffectiveQuantity.kt`
- `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/CorrectedAt.kt`
- `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/Reason.kt`
- `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/CorrectedAtTest.kt`
- `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/ReasonTest.kt`

- [ ] **Step 1: ディレクトリごと削除**

```bash
rm -rf domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/replenishment
rm -rf domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/consumption
rm domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/EffectiveQuantity.kt
rm domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/CorrectedAt.kt
rm domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/Reason.kt
rm domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/CorrectedAtTest.kt
rm domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/ReasonTest.kt
```

- [ ] **Step 2: ビルド & 全 domain テスト実行**

Run: `./gradlew :domain:check`
Expected: 全 PASS。失敗する場合は、残存している旧クラスへの import を grep:

```bash
grep -rn "model.stock.replenishment\|model.stock.consumption\|EffectiveQuantity\|CorrectedAt\|stock.Reason" domain/ backend/
```

ヒットしたファイルの import を修正(他モジュールで参照があれば該当ファイルも更新)。

- [ ] **Step 3: コミット**

```bash
git add -A
git commit -m "refactor(domain): remove obsolete Replenishment/Consumption/Correction classes"
```

---

## Task 5: `StockMovementsTable` を追加

**Files:**
- Create: `backend/infrastructure/schemas/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/schemas/stock/StockMovementsTable.kt`
- Delete: 旧 stock テーブル 4 ファイル

- [ ] **Step 1: `StockMovementsTable` を作成**

`backend/infrastructure/schemas/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/schemas/stock/StockMovementsTable.kt`:

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.schemas.stock

import net.brightroom.mindstock.domain.model.stock.movement.StockMovementType
import net.brightroom.mindstock.infrastructure.datasource.schemas.HistoryTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.product.ProductsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.user.UsersTable
import net.brightroom.mindstock.infrastructure.migration.annotation.Migratable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

@Migratable
object StockMovementsTable : HistoryTable("stock_movements") {
    val product_id = reference("product_id", ProductsTable.id, onDelete = ReferenceOption.RESTRICT)
    val type = enumerationByName<StockMovementType>("type", 20)
    val quantity = integer("quantity").check { it greater 0 }
    val occurred_at = timestampWithTimeZone("occurred_at")
    val acted_by = reference("acted_by", UsersTable.id, onDelete = ReferenceOption.RESTRICT)
    val note = text("note").default("")
    val created_at = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    init {
        index(false, product_id, id)
    }
}
```

- [ ] **Step 2: 旧 stock テーブル 4 ファイルを削除**

```bash
rm backend/infrastructure/schemas/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/schemas/stock/StockReplenishmentsTable.kt
rm backend/infrastructure/schemas/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/schemas/stock/StockConsumptionsTable.kt
rm backend/infrastructure/schemas/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/schemas/stock/StockReplenishmentCorrectionsTable.kt
rm backend/infrastructure/schemas/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/schemas/stock/StockConsumptionCorrectionsTable.kt
```

- [ ] **Step 3: `MigratableTables` を更新**

`backend/infrastructure/migration/detector/src/main/kotlin/net/brightroom/mindstock/infrastructure/migration/detector/MigratableTables.kt` を編集:

旧 import 4 行を削除:
```kotlin
import net.brightroom.mindstock.infrastructure.datasource.schemas.stock.StockConsumptionCorrectionsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.stock.StockConsumptionsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.stock.StockReplenishmentCorrectionsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.stock.StockReplenishmentsTable
```

新 import 1 行を追加:
```kotlin
import net.brightroom.mindstock.infrastructure.datasource.schemas.stock.StockMovementsTable
```

`all: List<Table>` リスト内で、旧 4 行(`StockReplenishmentsTable`, `StockConsumptionsTable`, `StockReplenishmentCorrectionsTable`, `StockConsumptionCorrectionsTable`)を削除し、`StockMovementsTable` 1 行に置き換え。

- [ ] **Step 4: schemas + detector がコンパイル通ることを確認**

Run: `./gradlew :backend:infrastructure:schemas:compileKotlin :backend:infrastructure:migration:detector:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: コミット**

```bash
git add backend/infrastructure/schemas backend/infrastructure/migration/detector
git commit -m "feat(schemas): replace 4 stock tables with unified stock_movements"
```

---

## Task 6: `init.sql` を再生成

**Files:**
- Modify: `backend/infrastructure/migration/executor/src/main/resources/db/migration/V20260523071825__init.sql`

- [ ] **Step 1: 既存 init.sql を削除して再生成**

`GenerateInitialMigrationManually` は対象パスへ書き込むだけで既存ファイル名は新タイムスタンプになる。
**まず**既存 `V20260523071825__init.sql` を削除しないと別名のファイルが生まれてしまう。

```bash
rm backend/infrastructure/migration/executor/src/main/resources/db/migration/V20260523071825__init.sql
```

注: 生成ジョブは `executor` モジュールではなく `generator` モジュールから書き込むため、出力先パスは generator 側からの相対(`src/main/resources/db/migration` = `generator/src/main/resources/db/migration`)になる可能性がある。実際の出力先を生成ログで確認すること。

- [ ] **Step 2: 生成ジョブを実行**

```bash
./gradlew :backend:infrastructure:migration:generator:test \
  --tests "*GenerateInitialMigrationManually" \
  -Dkotest.tags.exclude=
```

Expected: テストが PASS し、`Wrote ...V<timestamp>__init.sql (XXXX bytes)` がログに出る。

- [ ] **Step 3: 生成された init.sql の場所を確認して executor に移動**

```bash
find backend/infrastructure/migration -name "V*__init.sql" -newer backend/infrastructure/migration/executor/src/main/resources/db/migration/V20260523000001__append_only_role.sql
```

生成先が `generator/src/main/resources/db/migration/V<新timestamp>__init.sql` の場合、`executor` 側に移動 & rename:

```bash
mv backend/infrastructure/migration/generator/src/main/resources/db/migration/V*__init.sql \
   backend/infrastructure/migration/executor/src/main/resources/db/migration/V20260523071825__init.sql
```

(タイムスタンプは元の `V20260523071825` を維持する。Plan 2 のファイル名を保つことで Flyway 履歴の他環境への影響を最小化する。)

- [ ] **Step 4: 内容確認**

```bash
grep -E "stock_movements|stock_replenish|stock_consum" backend/infrastructure/migration/executor/src/main/resources/db/migration/V20260523071825__init.sql
```

Expected:
- `stock_movements` を含む CREATE TABLE 1 行が出る
- `stock_replenishments` / `stock_consumptions` / `stock_*_corrections` は **出ない**

- [ ] **Step 5: migration runner テストを実行して新 init.sql が当たることを確認**

Run: `./gradlew :backend:infrastructure:migration:executor:test`
Expected: 全 PASS(特に `MigrationRunnerTest`, `AppendOnlyEnforcementTest`)

- [ ] **Step 6: コミット**

```bash
git add backend/infrastructure/migration/executor/src/main/resources/db/migration/V20260523071825__init.sql
git commit -m "chore(migration): regenerate init.sql with stock_movements"
```

---

## Task 7: 親 spec への波及反映 + 全体ビルド確認

**Files:**
- Modify: `docs/superpowers/specs/2026-05-23-mindstock-design.md`

- [ ] **Step 1: 親 spec の関連節に supersession 注記を追加**

以下 4 箇所の先頭に `> 本節は [2026-05-24-stock-movements-unification-design.md](./2026-05-24-stock-movements-unification-design.md) で再設計済み。` を追記。原文は履歴として残す。

1. §4.3 ID 戦略 の冒頭(`### 4.3 ID 戦略` 直後)
2. §4.5 訂正の方針 の冒頭
3. §5.5 Stock ドメイン の冒頭
4. §6.1 サービスインターフェース の冒頭 — ただし stock 関連 RPC のみ影響なので「Stock 関連 RPC は再設計済」と限定して注記

各注記は 1 行のみ。原文の書き換えは行わない。

- [ ] **Step 2: ルート build を実行(全モジュールがクリーンであることを最終確認)**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL。
注: testcontainers を使うテストはローカル環境に Docker が必要。Docker が動いていない場合は `./gradlew check -x :backend:infrastructure:migration:executor:test` で範囲を絞ってもよい(ただし PR 時に CI で実行される)。

- [ ] **Step 3: コミット & PR 作成準備**

```bash
git add docs/superpowers/specs/2026-05-23-mindstock-design.md
git commit -m "docs(spec): mark stock sections as superseded by movements unification"
git log --oneline main..HEAD
```

PR を作成する場合(別途指示があれば):

```bash
git push -u origin refactor/stock-movements-unification
gh pr create --title "refactor: unify stock tables into stock_movements" --body "$(cat <<'EOF'
## Summary
- Stock 関連 4 テーブルを `stock_movements` 1 本に統合
- 訂正概念を廃止し、訂正は逆方向 movement の追加で表現
- `Stock.effective()` の値同値問題を解消(差分計上型のためロジック自体が不要に)

## Test plan
- [ ] `./gradlew :domain:check`
- [ ] `./gradlew :backend:infrastructure:migration:executor:test`
- [ ] `./gradlew build`

Spec: `docs/superpowers/specs/2026-05-24-stock-movements-unification-design.md`
Plan: `docs/superpowers/plans/2026-05-24-stock-movements-unification.md`

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```
