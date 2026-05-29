# Plan B: Stock/Movement Integrity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stock 集約周辺の型整合性を 5 つの独立 PR で改善する: (B-1) `StockMovementType` enum と `type` フィールドを domain から削除、(B-2) `StockMovement.product` 二重保持を解消し `replenish/consume` 戻り値を `Unit` 化、(B-3) `MinimumStock` を sealed polymorphic 化、(B-4) `Stocks` 集合体を新設して `ShoppingList` を委譲化、(B-5) Plan A follow-up として `latestNames` aliased subquery を 6 callsite で共通化。

**Architecture:** 5 PR は B-1 → B-2 → B-3 → B-4 → B-5 の順で独立 merge 可能。各 PR で wire 形式の破壊を許容（domain = wire-format 前提、spec §3.1）。B-3 だけは `@JvmInline value class` の `sealed interface` 実装可否を最初の Step で事前検証する。

**Tech Stack:** Kotlin 2.x Multiplatform / Exposed v1 / kotlinx-rpc 0.10.2 / kotlinx-serialization / Kotest

**Spec:** `docs/superpowers/specs/2026-05-29-domain-cohesion-coupling-design.md`（§6 Plan B 実装スコープ）

**前提:** Plan A（PR #65）が merge 済。`Profile` が `User` を置換、`DomainException` が削除されたコードを起点とする。本 Plan は internal refactor のため wire 互換性の維持は不要。

---

## File Plan

### B-1: StockMovementType 削除（所見 4.5）

- Delete: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/movement/StockMovementType.kt`
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/movement/StockMovement.kt` — `val type` 削除
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/movement/Replenishment.kt` — `@Transient override val type` 削除
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/movement/Consumption.kt` — 同上
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/stock/StockMovementType.kt` — infrastructure 内部 enum
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/stock/StockMovementsTable.kt` — import 差し替え
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/stock/StockHydration.kt` — import 差し替え（シグネチャ不変）
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/stock/StockRegisterDataSource.kt` — import 差し替え
- Modify: `domain/src/commonTest/.../model/SerializationRoundTripTest.kt` — `type` フィールド消滅に追従するアサーションがあれば修正
- Modify（必要なら）: `domain/src/commonTest/.../model/stock/movement/StockMovementsTest.kt` — `.type` 参照あれば `is Replenishment` 等で書き換え

### B-2: StockMovement.product 削除 + replenish/consume を Unit 戻り値に（所見 2.1）

- Modify: `domain/.../model/stock/movement/StockMovement.kt` — `val product: Product` 削除
- Modify: `domain/.../model/stock/movement/Replenishment.kt` — `override val product` 削除
- Modify: `domain/.../model/stock/movement/Consumption.kt` — `override val product` 削除
- Modify: `backend/core/.../infrastructure/datasource/stock/StockHydration.kt` — `toStockMovement` の `product` 引数削除、戻り値の構築から product 除去
- Modify: `backend/core/.../infrastructure/datasource/stock/StockDataSource.kt` — `toStockMovement` 呼び出しを引数変更に追従
- Modify: `backend/core/.../infrastructure/datasource/stock/StockRegisterDataSource.kt` — `replenish/consume` の戻り値を `Unit` に、`loadProfile` 削除
- Modify: `backend/core/.../application/repository/stock/StockRegisterRepository.kt` — `replenish/consume` 戻り値 `Unit`
- Modify: `backend/core/.../application/service/stock/StockRegisterService.kt` — `replenish/consume` 戻り値 `Unit`
- Modify: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/StockRpcService.kt` — `replenish/consume` 戻り値 `Unit`
- Modify: `backend/api/.../presentation/rpc/stock/StockController.kt` — 同上
- Modify: domain test 群（`product` パラメータ削除、`m.product` 参照削除）
  - `StockMovementsTest.kt`
  - `SerializationRoundTripTest.kt`
  - `StockTest.kt`
  - `ShoppingListTest.kt`
- Modify: backend/api integration / e2e
  - `StockDataSourceIntegrationTest.kt`
  - `StockRegisterDataSourceIntegrationTest.kt`
  - `StockRpcServiceE2eTest.kt`
  - `StockControllerTest.kt`

### B-3: MinimumStock polymorphic 化（所見 4.6 + 4.7）

- Modify: `domain/.../model/product/MinimumStock.kt` — `sealed interface` + `Set` / `NotSet`
- Modify: `domain/.../model/product/Product.kt` — `minimumStock: MinimumStock?` → `MinimumStock`（non-null）
- Modify: `domain/.../model/stock/Stock.kt` — `needsReplenishment/shortage` を `minimumStock.isBelow/shortage` に委譲
- Modify: `backend/core/.../infrastructure/datasource/product/ProductHydration.kt` — null → `NotSet`, non-null → `Set` 分岐
- Modify: `backend/core/.../application/repository/product/ProductRegisterRepository.kt` — `setMinimumStock(value: MinimumStock.Set)`
- Modify: `backend/core/.../application/service/product/ProductRegisterService.kt` — 同上
- Modify: `backend/core/.../infrastructure/datasource/product/ProductRegisterDataSource.kt` — 同上
- Modify: `rpc/.../ProductRpcService.kt` — `setMinimumStock(value: MinimumStock.Set)`
- Modify: `backend/api/.../presentation/rpc/product/ProductController.kt` — 同上
- Modify: domain test 群
  - `MinimumStockTest.kt`: `Set` / `NotSet` のテストに書き換え
  - `StockTest.kt`: `MinimumStock.Set(n)` / `MinimumStock.NotSet` 構築
  - `ShoppingListTest.kt`: 同上
  - `SerializationRoundTripTest.kt`: polymorphic round-trip テスト
- Modify: integration / e2e
  - `ProductRegisterDataSourceIntegrationTest.kt`
  - `ProductRpcServiceE2eTest.kt`

### B-4: Stocks 集合体新設 + ShoppingList 委譲（所見 2.2）

- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/Stocks.kt`
- Modify: `domain/.../model/shopping/ShoppingList.kt` — 引数を `Stocks` に変更、`stocks.needsReplenishment()` に委譲
- Modify: `backend/core/.../application/repository/stock/StockRepository.kt` — `stocksOf(household): Stocks`
- Modify: `backend/core/.../application/service/stock/StockService.kt` — `list(household): Stocks`
- Modify: `backend/core/.../infrastructure/datasource/stock/StockDataSource.kt` — `stocksOf` 戻り値型
- Modify: `rpc/.../StockRpcService.kt` — `list(householdId): Stocks`
- Modify: `backend/api/.../presentation/rpc/stock/StockController.kt` — 同上
- Modify: domain test
  - `ShoppingListTest.kt`: `Stocks` 構築
  - 新規 `domain/src/commonTest/.../model/stock/StocksTest.kt`: `needsReplenishment` のテスト
- Modify: backend/api integration / e2e
  - `StockDataSourceIntegrationTest.kt`
  - `StockRpcServiceE2eTest.kt`
  - `StockControllerTest.kt`

### B-5: latestNames aliased subquery 共通化（Plan A follow-up #1）

- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/user/LatestDisplayNames.kt` — 共通サブクエリヘルパ
- Modify: `backend/core/.../infrastructure/datasource/user/UserDataSource.kt` — `queryLatest` を共通ヘルパで置換
- Modify: `backend/core/.../infrastructure/datasource/household/HouseholdDataSource.kt` — `findOf`, `findById` の重複削除
- Modify: `backend/core/.../infrastructure/datasource/household/HouseholdRegisterDataSource.kt` — `create` の重複削除
- Modify: `backend/core/.../infrastructure/datasource/stock/StockDataSource.kt` — `movementHistory`, `loadMovementsFor` の重複削除

注: `StockRegisterDataSource.loadProfile` は B-2 で関数ごと削除されるため、B-5 の対象外。

---

## 新規/最終型の正準シグネチャ

### `StockMovement`（B-1 / B-2 完了後）

```kotlin
// domain/.../model/stock/movement/StockMovement.kt
package net.brightroom.mindstock.domain.model.stock.movement

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.user.profile.Profile

@Serializable
sealed interface StockMovement {
    val quantity: Quantity
    val occurredAt: OccurredAt
    val actor: Profile
    val note: Note
}
```

```kotlin
// domain/.../model/stock/movement/Replenishment.kt
@Serializable
data class Replenishment(
    override val quantity: Quantity,
    override val occurredAt: OccurredAt,
    override val actor: Profile,
    override val note: Note,
) : StockMovement
```

```kotlin
// domain/.../model/stock/movement/Consumption.kt
@Serializable
data class Consumption(
    override val quantity: Quantity,
    override val occurredAt: OccurredAt,
    override val actor: Profile,
    override val note: Note,
) : StockMovement
```

### `MinimumStock`（B-3 完了後）

```kotlin
// domain/.../model/product/MinimumStock.kt
package net.brightroom.mindstock.domain.model.product

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
sealed interface MinimumStock {
    fun isBelow(quantity: Int): Boolean
    fun shortage(quantity: Int): Int

    @Serializable
    data object NotSet : MinimumStock {
        override fun isBelow(quantity: Int): Boolean = false
        override fun shortage(quantity: Int): Int = 0
    }

    @Serializable
    @JvmInline
    value class Set(
        private val value: Int,
    ) : MinimumStock {
        init {
            require(value >= 0) { "minimum_stock must be >= 0, got $value" }
        }

        override fun isBelow(quantity: Int): Boolean = quantity < value
        override fun shortage(quantity: Int): Int = (value - quantity).coerceAtLeast(0)

        operator fun invoke(): Int = value
    }
}
```

### `Product`（B-3 完了後）

```kotlin
@Serializable
data class Product(
    val id: ProductId,
    val catalogItem: CatalogItem,
    val minimumStock: MinimumStock,  // non-null
    val archived: Boolean,
)
```

### `Stock`（B-3 完了後）

```kotlin
@Serializable
data class Stock(
    val product: Product,
    val movements: StockMovements,
) {
    fun currentQuantity(): Int = movements.netQuantity()

    fun needsReplenishment(): Boolean = product.minimumStock.isBelow(currentQuantity())

    fun shortage(): Int = product.minimumStock.shortage(currentQuantity())
}
```

### `Stocks`（B-4 新設）

```kotlin
// domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/Stocks.kt
package net.brightroom.mindstock.domain.model.stock

import kotlinx.serialization.Serializable

@Serializable
data class Stocks(
    val list: List<Stock>,
) {
    fun needsReplenishment(): List<Stock> = list.filter { it.needsReplenishment() }
}
```

### `ShoppingList`（B-4 完了後）

```kotlin
package net.brightroom.mindstock.domain.model.shopping

import net.brightroom.mindstock.domain.model.stock.Stocks

class ShoppingList(
    private val stocks: Stocks,
) {
    fun itemsToBuy(): List<ShoppingListItem> =
        stocks.needsReplenishment().map { ShoppingListItem(it, shortage = it.shortage()) }
}
```

### `LatestDisplayNames`（B-5 新設）

```kotlin
// backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/user/LatestDisplayNames.kt
package net.brightroom.mindstock.infrastructure.datasource.user

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.QueryAlias
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.select
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * user_id ごとの最新 display name 行を選ぶための aliased subquery。
 *
 * 使い方:
 * ```
 * val latest = latestDisplayNames()
 * UsersTable
 *   .join(latest.alias, JoinType.INNER, onColumn = UsersTable.id, otherColumn = latest.userId)
 *   .join(UserDisplayNamesTable, JoinType.INNER) {
 *       (UserDisplayNamesTable.user_id eq latest.userId) and
 *           (UserDisplayNamesTable.id eq latest.maxId)
 *   }
 * ```
 */
@OptIn(ExperimentalUuidApi::class)
internal class LatestDisplayNames(
    val alias: QueryAlias,
    val userId: Column<Uuid>,
    val maxId: Column<Long>,
)

@OptIn(ExperimentalUuidApi::class)
internal fun latestDisplayNames(): LatestDisplayNames {
    val maxIdAlias = UserDisplayNamesTable.id.max().alias("max_name_id")
    val alias =
        UserDisplayNamesTable
            .select(UserDisplayNamesTable.user_id, maxIdAlias)
            .groupBy(UserDisplayNamesTable.user_id)
            .alias("latest_names")
    @Suppress("UNCHECKED_CAST")
    return LatestDisplayNames(
        alias = alias,
        userId = alias[UserDisplayNamesTable.user_id] as Column<Uuid>,
        maxId = alias[maxIdAlias] as Column<Long>,
    )
}
```

注: 上記の `Column<T>` キャストは Exposed の `QueryAlias.get()` が `Expression<*>` を返すため必要。実装時に正確な型は Exposed v1 のドキュメントと既存コードの型注釈を見て確定する。代替案として `LatestDisplayNames` を関数ではなく `internal fun Query.joinLatestDisplayNames(...)` 形式の拡張で書く方法もあるが、共有したいのは「subquery + JOIN 条件」なので構造体化が素直。

---

## B-1: StockMovementType 廃止

**目的:** sealed `StockMovement` で網羅判別が可能なため `enum StockMovementType` と `type` フィールドは domain で冗長。infrastructure 層には DB の `enumerationByName` のために enum が必要なので、同名で infrastructure に移設する。

### Steps

- [ ] **Step B-1.1: infrastructure 層に `StockMovementType.kt` を新規作成**

`backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/stock/StockMovementType.kt` を作成:

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.stock

internal enum class StockMovementType {
    REPLENISHMENT,
    CONSUMPTION,
}
```

- [ ] **Step B-1.2: domain 側 `StockMovementType.kt` を削除**

```bash
git rm domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/movement/StockMovementType.kt
```

- [ ] **Step B-1.3: domain `StockMovement.kt` から `val type` 削除**

`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/movement/StockMovement.kt`:

```kotlin
package net.brightroom.mindstock.domain.model.stock.movement

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.user.profile.Profile

@Serializable
sealed interface StockMovement {
    val product: Product
    val quantity: Quantity
    val occurredAt: OccurredAt
    val actor: Profile
    val note: Note
}
```

注: `product` は B-2 で削除する。B-1 ではまだ残す。

- [ ] **Step B-1.4: `Replenishment.kt` から `@Transient override val type` 行と `Transient` import 削除**

```kotlin
package net.brightroom.mindstock.domain.model.stock.movement

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.user.profile.Profile

@Serializable
data class Replenishment(
    override val product: Product,
    override val quantity: Quantity,
    override val occurredAt: OccurredAt,
    override val actor: Profile,
    override val note: Note,
) : StockMovement
```

- [ ] **Step B-1.5: `Consumption.kt` も同様に修正**

```kotlin
package net.brightroom.mindstock.domain.model.stock.movement

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.user.profile.Profile

@Serializable
data class Consumption(
    override val product: Product,
    override val quantity: Quantity,
    override val occurredAt: OccurredAt,
    override val actor: Profile,
    override val note: Note,
) : StockMovement
```

- [ ] **Step B-1.6: `StockMovementsTable.kt` の import を新位置に差し替え**

`backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/stock/StockMovementsTable.kt` の冒頭:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.stock

// 削除: import net.brightroom.mindstock.domain.model.stock.movement.StockMovementType
// （同じ package の internal enum を使うので import 不要）
import net.brightroom.mindstock.infrastructure.datasource.HistoryTable
import net.brightroom.mindstock.infrastructure.datasource.product.ProductsTable
import net.brightroom.mindstock.infrastructure.datasource.user.UsersTable
// ... 以下既存通り
```

`enumerationByName<StockMovementType>("type", 20)` の参照先が同 package の internal enum に変わるが、コードは変更不要。

- [ ] **Step B-1.7: `StockHydration.kt` の import を差し替え**

`backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/stock/StockHydration.kt`:

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.stock

import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.movement.Consumption
import net.brightroom.mindstock.domain.model.stock.movement.Replenishment
import net.brightroom.mindstock.domain.model.stock.movement.StockMovement
// 削除: import net.brightroom.mindstock.domain.model.stock.movement.StockMovementType
import net.brightroom.mindstock.domain.model.user.profile.Profile
import kotlin.time.Instant

internal fun toStockMovement(
    product: Product,
    actor: Profile,
    type: StockMovementType,   // ← infrastructure 層の internal enum
    quantity: Int,
    occurredAt: Instant,
    note: String,
): StockMovement {
    val q = Quantity(quantity)
    val occurred = OccurredAt(occurredAt)
    val n = Note(note)
    return when (type) {
        StockMovementType.REPLENISHMENT -> Replenishment(product, q, occurred, actor, n)
        StockMovementType.CONSUMPTION -> Consumption(product, q, occurred, actor, n)
    }
}
```

- [ ] **Step B-1.8: `StockRegisterDataSource.kt` の import を差し替え**

```kotlin
// 削除: import net.brightroom.mindstock.domain.model.stock.movement.StockMovementType
```

`StockMovementType.REPLENISHMENT` / `CONSUMPTION` の参照は同 package 内の internal enum を解決するので変更不要。

- [ ] **Step B-1.9: domain test の `.type` 参照を確認**

```bash
grep -rn "\.type\b" domain/src/commonTest --include="*.kt" | grep -iE "movement|replenishment|consumption"
grep -rn "StockMovementType" --include="*.kt" .
```

結果に応じて:
- domain test に `.type` 参照や `StockMovementType.X` 参照があれば `is Replenishment` / `is Consumption` に書き換え（現状の grep では出ない想定）

- [ ] **Step B-1.10: `SerializationRoundTripTest.kt` の wire 期待値の確認**

現在のテストは `roundTrip(value, Stock.serializer()) shouldBe value` 形式で deep equality を見ているだけなので、`type` フィールドが消えても deserialize → re-encode → re-decode の往復が成立すれば通る。**変更不要の想定**。

ただし sealed StockMovement の polymorphic discriminator がデフォルトの `type` キーを使うことを念のため確認する。`kotlinx.serialization` 1.x のデフォルト discriminator key は `"type"`、value はクラス FQN。`val type` フィールド消滅後は名前衝突がないので OK。

`Json { encodeDefaults = true }` の挙動も変わらない（消えたフィールドは encode されない）。

- [ ] **Step B-1.11: ビルドと全テスト**

```bash
./gradlew clean build
```

期待: BUILD SUCCESSFUL、全テスト pass。

- [ ] **Step B-1.12: 動作確認の手動テストはスキップ**

wire 形式は変わるが frontend に stock 画面が無いため、動作影響は backend/api e2e の通過のみ。

- [ ] **Step B-1.13: コミット**

```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor(domain): remove redundant StockMovementType from domain

sealed StockMovement で網羅判別が可能なため、StockMovementType enum
と StockMovement.type フィールドを domain から削除。infrastructure 層
の DB 列マッピングには引き続き enum が必要なので、同名の internal enum
を infrastructure/datasource/stock に移設。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step B-1.14: PR 作成**

```bash
git push -u origin HEAD
gh pr create --title "refactor(domain): remove redundant StockMovementType" --body "$(cat <<'EOF'
## Summary
- sealed StockMovement で網羅判別が可能なため、enum StockMovementType と val type フィールドを domain から削除
- infrastructure 層の DB 列マッピング用に同名 internal enum を infrastructure/datasource/stock に移設
- wire 形式から `type` フィールドが消える（許容変更、spec §3.1）

## Test plan
- [x] `./gradlew clean build` 成功
- [x] domain test 全 pass
- [x] backend/api e2e 全 pass

Spec: docs/superpowers/specs/2026-05-29-domain-cohesion-coupling-design.md (§4.5 / §6)
EOF
)"
```

---

## B-2: StockMovement.product 削除 + replenish/consume を Unit 戻り値に

**目的:** `Stock.product` と `StockMovement.product` の二重保持を解消。集約ルートに product を一元化することで「全 movement の product が一致」という不変条件を型で表現する。あわせて `replenish/consume` の戻り値を `Unit` 化し、書き込み後の actor Profile lookup（`loadProfile`、過剰 GROUP BY）を自然消滅させる。

### Steps

- [ ] **Step B-2.1: domain `StockMovement.kt` から `val product` 削除**

```kotlin
package net.brightroom.mindstock.domain.model.stock.movement

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.user.profile.Profile

@Serializable
sealed interface StockMovement {
    val quantity: Quantity
    val occurredAt: OccurredAt
    val actor: Profile
    val note: Note
}
```

`Product` import を削除。

- [ ] **Step B-2.2: `Replenishment.kt` から `product` 削除**

```kotlin
package net.brightroom.mindstock.domain.model.stock.movement

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.user.profile.Profile

@Serializable
data class Replenishment(
    override val quantity: Quantity,
    override val occurredAt: OccurredAt,
    override val actor: Profile,
    override val note: Note,
) : StockMovement
```

- [ ] **Step B-2.3: `Consumption.kt` も同様**

```kotlin
package net.brightroom.mindstock.domain.model.stock.movement

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.user.profile.Profile

@Serializable
data class Consumption(
    override val quantity: Quantity,
    override val occurredAt: OccurredAt,
    override val actor: Profile,
    override val note: Note,
) : StockMovement
```

- [ ] **Step B-2.4: `StockHydration.kt` の `toStockMovement` シグネチャから `product` 削除**

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.stock

import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.movement.Consumption
import net.brightroom.mindstock.domain.model.stock.movement.Replenishment
import net.brightroom.mindstock.domain.model.stock.movement.StockMovement
import net.brightroom.mindstock.domain.model.user.profile.Profile
import kotlin.time.Instant

internal fun toStockMovement(
    actor: Profile,
    type: StockMovementType,
    quantity: Int,
    occurredAt: Instant,
    note: String,
): StockMovement {
    val q = Quantity(quantity)
    val occurred = OccurredAt(occurredAt)
    val n = Note(note)
    return when (type) {
        StockMovementType.REPLENISHMENT -> Replenishment(q, occurred, actor, n)
        StockMovementType.CONSUMPTION -> Consumption(q, occurred, actor, n)
    }
}
```

`Product` import を削除。

- [ ] **Step B-2.5: `StockDataSource.kt` の `toStockMovement` 呼び出しを更新**

`movementHistory` 内:

```kotlin
toStockMovement(
    actor = row.toProfile(),
    type = row[StockMovementsTable.type],
    quantity = row[StockMovementsTable.quantity],
    occurredAt = row[StockMovementsTable.occurred_at].toInstant().toKotlinInstant(),
    note = row[StockMovementsTable.note],
)
```

`loadMovementsFor` 内（productByUuid のままで OK、戻り値 `Pair<Uuid, StockMovement>` のキー側で product を結びつける）:

```kotlin
.map { row ->
    val productUuid = row[StockMovementsTable.product_id]
    // productByUuid は呼び出し側で stock 構築用に保持しているが、movement 構築には不要
    productUuid to
        toStockMovement(
            actor = row.toProfile(),
            type = row[StockMovementsTable.type],
            quantity = row[StockMovementsTable.quantity],
            occurredAt = row[StockMovementsTable.occurred_at].toInstant().toKotlinInstant(),
            note = row[StockMovementsTable.note],
        )
}
```

`productByUuid` の参照箇所は `loadMovementsFor` のみで、戻り値の Map<Uuid, List<StockMovement>> に product を埋め込む必要はなくなったので `productByUuid` 変数は削除可能。

- [ ] **Step B-2.6: `StockRegisterDataSource.kt` の `replenish/consume` を Unit 戻り値に、`loadProfile` を削除**

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.stock

import net.brightroom.mindstock.application.repository.stock.StockRegisterRepository
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.user.UserId
import org.jetbrains.exposed.v1.jdbc.insert
import java.time.ZoneOffset
import kotlin.time.toJavaInstant
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class StockRegisterDataSource : StockRegisterRepository {
    override fun replenish(
        product: Product,
        quantity: Quantity,
        occurredAt: OccurredAt,
        by: UserId,
        note: Note,
    ) {
        insertMovement(product, quantity, occurredAt, by, note, StockMovementType.REPLENISHMENT)
    }

    override fun consume(
        product: Product,
        quantity: Quantity,
        occurredAt: OccurredAt,
        by: UserId,
        note: Note,
    ) {
        insertMovement(product, quantity, occurredAt, by, note, StockMovementType.CONSUMPTION)
    }

    private fun insertMovement(
        product: Product,
        quantity: Quantity,
        occurredAt: OccurredAt,
        actor: UserId,
        note: Note,
        type: StockMovementType,
    ) {
        StockMovementsTable.insert {
            it[product_id] = product.id()
            it[StockMovementsTable.type] = type
            it[StockMovementsTable.quantity] = quantity()
            it[occurred_at] = occurredAt().toJavaInstant().atOffset(ZoneOffset.UTC)
            it[acted_by] = actor()
            it[StockMovementsTable.note] = note()
        }
    }
}
```

Removed: `loadProfile`, `Replenishment` / `Consumption` / `Profile` / `latestNames` 関連 import。

- [ ] **Step B-2.7: `StockRegisterRepository.kt` の戻り値を `Unit` に**

```kotlin
package net.brightroom.mindstock.application.repository.stock

import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.user.UserId

interface StockRegisterRepository {
    fun replenish(
        product: Product,
        quantity: Quantity,
        occurredAt: OccurredAt,
        by: UserId,
        note: Note,
    )

    fun consume(
        product: Product,
        quantity: Quantity,
        occurredAt: OccurredAt,
        by: UserId,
        note: Note,
    )
}
```

import から `Replenishment` / `Consumption` を削除。

- [ ] **Step B-2.8: `StockRegisterService.kt` も戻り値を `Unit` に**

```kotlin
class StockRegisterService(
    private val stockRegisterRepository: StockRegisterRepository,
) {
    fun replenish(
        product: Product,
        quantity: Quantity,
        occurredAt: OccurredAt,
        by: UserId,
        note: Note,
    ) {
        stockRegisterRepository.replenish(product, quantity, occurredAt, by, note)
    }

    fun consume(
        product: Product,
        quantity: Quantity,
        occurredAt: OccurredAt,
        by: UserId,
        note: Note,
    ) {
        stockRegisterRepository.consume(product, quantity, occurredAt, by, note)
    }
}
```

実装ファイルの現状確認: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/stock/StockRegisterService.kt` を読んで、上記の通りに更新（`Replenishment` / `Consumption` import 削除）。

- [ ] **Step B-2.9: `StockRpcService.kt` の戻り値を `Unit` に**

```kotlin
package net.brightroom.mindstock.rpc

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.Stock
import net.brightroom.mindstock.domain.model.stock.movement.StockMovements

@Rpc
interface StockRpcService {
    suspend fun get(productId: ProductId): Stock

    suspend fun list(householdId: HouseholdId): List<Stock>

    suspend fun movementHistory(
        productId: ProductId,
        limit: Int,
    ): StockMovements

    suspend fun replenish(
        productId: ProductId,
        qty: Quantity,
        occurredAt: OccurredAt,
        note: Note,
    )

    suspend fun consume(
        productId: ProductId,
        qty: Quantity,
        occurredAt: OccurredAt,
        note: Note,
    )
}
```

import から `Replenishment` / `Consumption` を削除。`list` の `List<Stock>` は B-4 で `Stocks` に変える。

- [ ] **Step B-2.10: `StockController.kt` の戻り値を `Unit` に**

`replenish` / `consume` のメソッドシグネチャから戻り値型を削除し、body の `return` 形を消す:

```kotlin
override suspend fun replenish(
    productId: ProductId,
    qty: Quantity,
    occurredAt: OccurredAt,
    note: Note,
) {
    tx(database) {
        // TODO(authz): verify actor can modify product $productId (member of its household)
        val product =
            productRepository.findById(productId)
                ?: throw NotFoundException("product not found: $productId")
        stockRegisterService.replenish(product, qty, occurredAt, actor.userId, note)
    }
}

override suspend fun consume(
    productId: ProductId,
    qty: Quantity,
    occurredAt: OccurredAt,
    note: Note,
) {
    tx(database) {
        // TODO(authz): verify actor can modify product $productId (member of its household)
        val product =
            productRepository.findById(productId)
                ?: throw NotFoundException("product not found: $productId")
        stockRegisterService.consume(product, qty, occurredAt, actor.userId, note)
    }
}
```

import から `Replenishment` / `Consumption` を削除。

- [ ] **Step B-2.11: domain test 群を更新**

確認コマンド:

```bash
grep -rn "Replenishment(\|Consumption(" --include="*.kt" .
grep -rn "\.product\b" --include="*.kt" domain backend/api/src/test backend/core/src/main | grep -iE "movement|replenishment|consumption"
```

更新対象 domain test:

`domain/src/commonTest/.../model/stock/movement/StockMovementsTest.kt`:

```kotlin
private fun replenish(qty: Int) = Replenishment(Quantity(qty), occurred(), profile, Note(""))
private fun consume(qty: Int) = Consumption(Quantity(qty), occurred(), profile, Note(""))
```

import `Product` などが不要になれば削除。`product` フィールド削除に追従。

`domain/src/commonTest/.../model/stock/StockTest.kt`:

```kotlin
private fun replenish(
    product: Product,
    qty: Int,
) = Replenishment(Quantity(qty), occurred(), profile, Note(""))

private fun consume(
    product: Product,
    qty: Int,
) = Consumption(Quantity(qty), occurred(), profile, Note(""))
```

注: ヘルパ引数の `product` は Stock を構築するために残す（Stock の側で product を持つ）。Replenishment 側からは消す。

`domain/src/commonTest/.../model/shopping/ShoppingListTest.kt`:

```kotlin
val movements =
    if (currentReplenished > 0) {
        listOf(
            Replenishment(
                quantity = Quantity(currentReplenished),
                occurredAt = OccurredAt(Instant.parse("2026-05-23T10:00:00Z"), now),
                actor = profile,
                note = Note(""),
            ),
        )
    } else {
        emptyList()
    }
```

`product = product,` 行を削除。

`domain/src/commonTest/.../model/SerializationRoundTripTest.kt`:

```kotlin
private val replenishment: StockMovement =
    Replenishment(
        quantity = Quantity(5),
        occurredAt = OccurredAt(Instant.parse("2026-05-25T10:00:00Z")),
        actor = profile,
        note = Note(""),
    )

private val consumption: StockMovement =
    Consumption(
        quantity = Quantity(1),
        occurredAt = OccurredAt(Instant.parse("2026-05-25T11:00:00Z")),
        actor = profile,
        note = Note("breakfast"),
    )
```

`product = product,` 行を削除。

- [ ] **Step B-2.12: backend/api integration / e2e test を更新**

```bash
grep -rn "Replenishment(\|Consumption(" backend/api/src/test --include="*.kt"
```

`StockDataSourceIntegrationTest.kt`, `StockRegisterDataSourceIntegrationTest.kt`, `StockRpcServiceE2eTest.kt`, `StockControllerTest.kt` で:

1. `Replenishment(product, ...)` / `Consumption(product, ...)` の `product` 引数を削除
2. `replenish(...)` / `consume(...)` の戻り値を期待しているテストは、書き込み後 `movementHistory(...)` で再取得して assert するように変更（または `stockOf(product)` で current quantity を見る）

例: `StockRegisterDataSourceIntegrationTest.kt` で「補充後の Replenishment 戻り値を assert」している箇所:

```kotlin
// Before
val result = stockRegister.replenish(product, Quantity(3), occurredAt, user.userId, Note(""))
result.quantity() shouldBe 3

// After
stockRegister.replenish(product, Quantity(3), occurredAt, user.userId, Note(""))
val history = stockData.movementHistory(product, limit = 1)
history.list.size shouldBe 1
val movement = history.list.single()
movement.shouldBeInstanceOf<Replenishment>()
movement.quantity() shouldBe 3
```

`StockControllerTest.kt` でも同様の方針。

- [ ] **Step B-2.13: ビルドと全テスト**

```bash
./gradlew clean build
```

期待: BUILD SUCCESSFUL、全テスト pass。

- [ ] **Step B-2.14: コミット**

```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor(domain): remove redundant product from StockMovement

Stock.product と StockMovement.product の二重保持を解消。集約ルートに
product を一元化することで「全 movement の product が Stock.product と
一致」という不変条件を型で表現する。

副次変更: StockRegisterRepository.replenish/consume の戻り値を Unit に。
書き込み直後の actor Profile lookup (loadProfile の過剰 GROUP BY) を
自然消滅させ、必要なクライアントは movementHistory を再取得する方針。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step B-2.15: PR 作成**

```bash
git push -u origin HEAD
gh pr create --title "refactor(domain): remove product field from StockMovement" --body "$(cat <<'EOF'
## Summary
- Stock.product と StockMovement.product の二重保持を解消（集約ルートに一元化）
- replenish/consume の戻り値を Unit に（loadProfile の過剰 GROUP BY が自然消滅）
- wire 形式から `product` フィールドが消える、戻り値も消える（許容変更、spec §3.1）

## Test plan
- [x] `./gradlew clean build` 成功
- [x] domain test 全 pass
- [x] backend/api integration/e2e 全 pass

Spec: docs/superpowers/specs/2026-05-29-domain-cohesion-coupling-design.md (§2.1 / §6)
EOF
)"
```

---

## B-3: MinimumStock polymorphic 化

**目的:** `Product.minimumStock` が null で「未設定」を表現しているのを sealed `MinimumStock { NotSet, Set }` に変える。Stock 側の `minimumStock?.let { ... }` 二重ロジックを `minimumStock.isBelow/shortage` に委譲。

### Steps

- [ ] **Step B-3.1: 事前検証 — `@JvmInline value class` が `sealed interface` を実装できるか**

`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/Sandbox.kt` を一時的に作成:

```kotlin
package net.brightroom.mindstock.domain

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
internal sealed interface SandboxFoo {
    @Serializable
    data object Bar : SandboxFoo

    @Serializable
    @JvmInline
    value class Baz(val v: Int) : SandboxFoo
}
```

ビルドして検証:

```bash
./gradlew :domain:compileKotlinJvm :domain:compileKotlinJs
```

期待: BUILD SUCCESSFUL。失敗した場合は本 Plan の Phase B-3 を中断し、代替案（`data class Set(val value: Int) : MinimumStock` で `@JvmInline` を諦め）に切り替える。

検証完了後ファイル削除:

```bash
rm domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/Sandbox.kt
```

- [ ] **Step B-3.2: TDD — `MinimumStockTest.kt` を書き換える（先にテストを更新して赤に）**

`domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/product/MinimumStockTest.kt`:

```kotlin
package net.brightroom.mindstock.domain.model.product

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class MinimumStockTest {
    @Test
    fun `Set accepts zero`() {
        MinimumStock.Set(0)()
            .shouldBe(0)
    }

    @Test
    fun `Set accepts positive`() {
        MinimumStock.Set(10)()
            .shouldBe(10)
    }

    @Test
    fun `Set rejects negative`() {
        shouldThrow<IllegalArgumentException> { MinimumStock.Set(-1) }
    }

    @Test
    fun `Set isBelow is true when quantity is strictly less than value`() {
        MinimumStock.Set(5).isBelow(4).shouldBeTrue()
        MinimumStock.Set(5).isBelow(5).shouldBeFalse()
        MinimumStock.Set(5).isBelow(6).shouldBeFalse()
    }

    @Test
    fun `Set shortage is value minus quantity coerced at zero`() {
        MinimumStock.Set(5).shortage(2) shouldBe 3
        MinimumStock.Set(5).shortage(5) shouldBe 0
        MinimumStock.Set(5).shortage(10) shouldBe 0
    }

    @Test
    fun `NotSet isBelow is always false`() {
        MinimumStock.NotSet.isBelow(0).shouldBeFalse()
        MinimumStock.NotSet.isBelow(100).shouldBeFalse()
    }

    @Test
    fun `NotSet shortage is always zero`() {
        MinimumStock.NotSet.shortage(0) shouldBe 0
        MinimumStock.NotSet.shortage(100) shouldBe 0
    }
}
```

- [ ] **Step B-3.3: 期待通り compile エラーが出ることを確認**

```bash
./gradlew :domain:compileTestKotlinJvm 2>&1 | tail -20
```

期待: `MinimumStock.Set` などが未定義でコンパイルエラー。

- [ ] **Step B-3.4: `MinimumStock.kt` を sealed interface に書き換え**

「新規/最終型の正準シグネチャ」セクションの `MinimumStock` をそのまま書き出す。

- [ ] **Step B-3.5: `MinimumStockTest.kt` のみで pass を確認**

```bash
./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.product.MinimumStockTest"
```

期待: 7 件すべて pass。

- [ ] **Step B-3.6: `Product.kt` の `minimumStock` を non-null に**

```kotlin
@Serializable
data class Product(
    val id: ProductId,
    val catalogItem: CatalogItem,
    val minimumStock: MinimumStock,
    val archived: Boolean,
)
```

- [ ] **Step B-3.7: `Stock.kt` の `needsReplenishment` / `shortage` を委譲に書き換え**

```kotlin
@Serializable
data class Stock(
    val product: Product,
    val movements: StockMovements,
) {
    fun currentQuantity(): Int = movements.netQuantity()

    fun needsReplenishment(): Boolean = product.minimumStock.isBelow(currentQuantity())

    fun shortage(): Int = product.minimumStock.shortage(currentQuantity())
}
```

- [ ] **Step B-3.8: `ProductHydration.kt` を `Set` / `NotSet` 分岐に**

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.product

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.ProductId
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal fun hydrateProduct(
    id: Uuid,
    catalogItem: CatalogItem,
    minimumStock: Int?,
    archived: Boolean,
): Product =
    Product(
        id = ProductId(id),
        catalogItem = catalogItem,
        minimumStock = if (minimumStock != null) MinimumStock.Set(minimumStock) else MinimumStock.NotSet,
        archived = archived,
    )
```

- [ ] **Step B-3.9: `ProductRegisterRepository.kt` の `setMinimumStock` 引数を `MinimumStock.Set` に**

```kotlin
fun setMinimumStock(
    product: Product,
    value: MinimumStock.Set,
    editedBy: UserId,
)
```

注: 「unset」操作の API は本 Plan のスコープ外。必要になったら別 API で追加。

- [ ] **Step B-3.10: `ProductRegisterService.kt` も `MinimumStock.Set` に**

```kotlin
fun setMinimumStock(
    product: Product,
    value: MinimumStock.Set,
    editedBy: UserId,
) {
    productRegisterRepository.setMinimumStock(product, value, editedBy)
}
```

- [ ] **Step B-3.11: `ProductRegisterDataSource.kt` の実装を更新**

```kotlin
override fun setMinimumStock(
    product: Product,
    value: MinimumStock.Set,
    editedBy: UserId,
) {
    ProductMinimumStocksTable.insert {
        it[product_id] = product.id()
        it[minimum_stock] = value()
        it[edited_by] = editedBy()
    }
}
```

- [ ] **Step B-3.12: `ProductRpcService.kt` も `MinimumStock.Set` に**

`rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/ProductRpcService.kt` を読み、`setMinimumStock(productId: ProductId, value: MinimumStock)` シグネチャを `setMinimumStock(productId: ProductId, value: MinimumStock.Set)` に変更。

- [ ] **Step B-3.13: `ProductController.kt` も `MinimumStock.Set` に**

`backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/product/ProductController.kt` を読み、`setMinimumStock` の引数型を追従。

- [ ] **Step B-3.14: `SerializationRoundTripTest.kt` に polymorphic round-trip テスト追加**

```kotlin
import net.brightroom.mindstock.domain.model.product.MinimumStock

// 既存 product 定義の minimumStock を MinimumStock.Set(2) に
private val product =
    Product(
        id = ProductId(Uuid.parse("00000000-0000-0000-0000-000000000003")),
        catalogItem = catalogItem,
        minimumStock = MinimumStock.Set(2),
        archived = false,
    )

@Test
fun `MinimumStock Set round-trip`() {
    val v: MinimumStock = MinimumStock.Set(7)
    roundTrip(v, MinimumStock.serializer()) shouldBe v
}

@Test
fun `MinimumStock NotSet round-trip`() {
    val v: MinimumStock = MinimumStock.NotSet
    roundTrip(v, MinimumStock.serializer()) shouldBe v
}

@Test
fun `Product with NotSet minimumStock round-trip`() {
    val productNotSet = product.copy(minimumStock = MinimumStock.NotSet)
    roundTrip(productNotSet, Product.serializer()) shouldBe productNotSet
}
```

- [ ] **Step B-3.15: その他のテストファイルで `MinimumStock(n)` を `MinimumStock.Set(n)` に置換**

確認コマンド:

```bash
grep -rn "MinimumStock(" --include="*.kt" .
```

更新対象:

`domain/src/commonTest/.../model/stock/StockTest.kt`:

```kotlin
private fun productWithMin(min: Int?) =
    Product(
        id = ProductId(Uuid.generateV7()),
        catalogItem = ...,
        minimumStock = if (min != null) MinimumStock.Set(min) else MinimumStock.NotSet,
        archived = false,
    )
```

`domain/src/commonTest/.../model/stock/movement/StockMovementsTest.kt`:

```kotlin
private val product =
    Product(
        id = ProductId(Uuid.generateV7()),
        catalogItem = ...,
        minimumStock = MinimumStock.NotSet,
        archived = false,
    )
```

`domain/src/commonTest/.../model/shopping/ShoppingListTest.kt`:

```kotlin
minimumStock = MinimumStock.Set(min),
```

`backend/api/.../infrastructure/datasource/repository/product/ProductRegisterDataSourceIntegrationTest.kt`:

```kotlin
tx { productRegister.setMinimumStock(product, MinimumStock.Set(2), user.userId) }
tx { productRegister.setMinimumStock(product, MinimumStock.Set(5), user.userId) }
// ...
refetched?.minimumStock shouldBe MinimumStock.Set(5)
```

`backend/api/.../e2e/product/ProductRpcServiceE2eTest.kt`:

```kotlin
rpc.setMinimumStock(product.id, MinimumStock.Set(3))
// ...
updated.minimumStock shouldBe MinimumStock.Set(3)
// ...
rpc.setMinimumStock(ProductId(Uuid.random()), MinimumStock.Set(1))
```

`adopt` 直後の Product は `MinimumStock.NotSet` を持つようになる点に注意。テストで `adopt` 後の `minimumStock` を確認している箇所があれば `MinimumStock.NotSet` に追従。

- [ ] **Step B-3.16: ビルドと全テスト**

```bash
./gradlew clean build
```

期待: BUILD SUCCESSFUL、全テスト pass。

- [ ] **Step B-3.17: 動作確認**

backend を起動し、frontend が無い stock 画面以外で product/minimumStock を扱う E2E ルートが通ることを確認。frontend 側で product setMinimumStock を扱う UI が存在するなら手動操作で確認、なければスキップ。

```bash
grep -rn "setMinimumStock" frontend/src --include="*.kt"
```

該当があれば dev server で動作確認、なければスキップ。

- [ ] **Step B-3.18: コミット**

```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor(domain): make MinimumStock polymorphic (Set / NotSet)

MinimumStock を sealed interface に変え、null で「未設定」を表現する
代わりにドメイン型 NotSet で表現。Stock 側の needsReplenishment /
shortage は MinimumStock.isBelow / shortage に委譲。

書き込み API (setMinimumStock) は MinimumStock.Set のみ受け取る契約に。
NotSet を書き込む操作が必要になったら別 API で追加。

Wire 形式: kotlinx.serialization 標準 polymorphic (type discriminator)
を採用。Int? null=NotSet の旧 wire 形式から破壊変更。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step B-3.19: PR 作成**

```bash
git push -u origin HEAD
gh pr create --title "refactor(domain): make MinimumStock polymorphic" --body "$(cat <<'EOF'
## Summary
- MinimumStock を sealed interface に (NotSet / Set)
- Product.minimumStock を non-null に
- Stock の needsReplenishment / shortage を MinimumStock 側に委譲
- setMinimumStock API は MinimumStock.Set のみ受け取る契約
- Wire 形式は kotlinx.serialization 標準 polymorphic に (Int? からの破壊変更)

## Test plan
- [x] `@JvmInline value class : sealed interface` の事前検証 OK
- [x] `./gradlew clean build` 成功
- [x] domain test 全 pass (NotSet/Set 両方の round-trip テスト追加)
- [x] backend/api integration/e2e 全 pass

Spec: docs/superpowers/specs/2026-05-29-domain-cohesion-coupling-design.md (§4.6 / §6)
EOF
)"
```

---

## B-4: Stocks 集合体新設 + ShoppingList 委譲

**目的:** `Products / CatalogItems / HouseholdMembers / StockMovements` と並ぶ集合体として `Stocks` を導入。`needsReplenishment` filter ロジックを Stocks に集約し、`ShoppingList` は薄いラッパに。

### Steps

- [ ] **Step B-4.1: TDD — `StocksTest.kt` を新規作成（先にテストで赤に）**

`domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/StocksTest.kt`:

```kotlin
package net.brightroom.mindstock.domain.model.stock

import io.kotest.matchers.collections.shouldContainExactly
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
import net.brightroom.mindstock.domain.model.stock.movement.Replenishment
import net.brightroom.mindstock.domain.model.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile
import kotlin.test.Test
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class StocksTest {
    private val profile =
        Profile(UserId(Uuid.generateV7()), DisplayName("alice"))
    private val now = Instant.parse("2026-05-24T10:00:00Z")

    private fun occurred() =
        OccurredAt(LocalDateTime(2026, 5, 1, 10, 0).toInstant(TimeZone.UTC), now)

    private fun stockOf(min: Int, currentReplenished: Int): Stock {
        val product =
            Product(
                id = ProductId(Uuid.generateV7()),
                catalogItem =
                    CatalogItem(
                        id = CatalogItemId(Uuid.generateV7()),
                        name = CatalogItemName("X"),
                        unit = CatalogItemUnit("個"),
                    ),
                minimumStock = MinimumStock.Set(min),
                archived = false,
            )
        val movements =
            if (currentReplenished > 0) {
                listOf(Replenishment(Quantity(currentReplenished), occurred(), profile, Note("")))
            } else {
                emptyList()
            }
        return Stock(product, StockMovements(movements))
    }

    @Test
    fun `needsReplenishment returns stocks below minimum only`() {
        val low = stockOf(min = 5, currentReplenished = 2)
        val ok = stockOf(min = 5, currentReplenished = 10)
        val stocks = Stocks(listOf(low, ok))

        stocks.needsReplenishment() shouldContainExactly listOf(low)
    }

    @Test
    fun `needsReplenishment returns empty when all are sufficient`() {
        val a = stockOf(min = 5, currentReplenished = 10)
        val b = stockOf(min = 5, currentReplenished = 6)
        val stocks = Stocks(listOf(a, b))

        stocks.needsReplenishment() shouldBe emptyList()
    }

    @Test
    fun `list is exposed directly`() {
        val s = stockOf(min = 5, currentReplenished = 2)
        Stocks(listOf(s)).list shouldContainExactly listOf(s)
    }
}
```

注: ここで構築する `Replenishment` / `MinimumStock.Set` は B-2 / B-3 完了後の形式。

- [ ] **Step B-4.2: 期待通り compile エラーが出ることを確認**

```bash
./gradlew :domain:compileTestKotlinJvm 2>&1 | tail -10
```

期待: `Stocks` が未定義。

- [ ] **Step B-4.3: `Stocks.kt` を新規作成**

`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/Stocks.kt`:

```kotlin
package net.brightroom.mindstock.domain.model.stock

import kotlinx.serialization.Serializable

@Serializable
data class Stocks(
    val list: List<Stock>,
) {
    fun needsReplenishment(): List<Stock> = list.filter { it.needsReplenishment() }
}
```

- [ ] **Step B-4.4: `StocksTest.kt` を pass させる**

```bash
./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.stock.StocksTest"
```

期待: 3 件すべて pass。

- [ ] **Step B-4.5: `ShoppingList.kt` を Stocks 委譲に**

`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/shopping/ShoppingList.kt`:

```kotlin
package net.brightroom.mindstock.domain.model.shopping

import net.brightroom.mindstock.domain.model.stock.Stocks

class ShoppingList(
    private val stocks: Stocks,
) {
    fun itemsToBuy(): List<ShoppingListItem> =
        stocks.needsReplenishment().map { ShoppingListItem(it, shortage = it.shortage()) }
}
```

- [ ] **Step B-4.6: `ShoppingListTest.kt` を Stocks 構築に更新**

`domain/src/commonTest/.../model/shopping/ShoppingListTest.kt`:

```kotlin
import net.brightroom.mindstock.domain.model.stock.Stocks
// ...

@Test
fun `itemsToBuy returns only stocks below minimum`() {
    val low = stockOf("a", min = 5, currentReplenished = 2)
    val ok = stockOf("b", min = 5, currentReplenished = 10)
    val list = ShoppingList(Stocks(listOf(low, ok)))

    val result = list.itemsToBuy()
    result.size shouldBe 1
    result[0].stock shouldBe low
    result[0].shortage shouldBe 3
}
```

- [ ] **Step B-4.7: `StockRepository.kt` の戻り値を `Stocks` に**

`backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/stock/StockRepository.kt`:

```kotlin
package net.brightroom.mindstock.application.repository.stock

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Stock
import net.brightroom.mindstock.domain.model.stock.Stocks
import net.brightroom.mindstock.domain.model.stock.movement.StockMovements

interface StockRepository {
    fun stockOf(product: Product): Stock

    fun stocksOf(household: Household): Stocks

    fun movementHistory(
        product: Product,
        limit: Int = 50,
    ): StockMovements
}
```

- [ ] **Step B-4.8: `StockService.kt` の戻り値を `Stocks` に**

`backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/stock/StockService.kt` で `list` の型を更新:

```kotlin
fun list(household: Household): Stocks = stockRepository.stocksOf(household)
```

import に `Stocks` を追加、不要なら `List<Stock>` 由来の `kotlin.collections.List` import を整理。

- [ ] **Step B-4.9: `StockDataSource.stocksOf` を `Stocks` 返却に**

```kotlin
override fun stocksOf(household: Household): Stocks {
    val products = productRepository.listOf(household).asList()
    if (products.isEmpty()) return Stocks(emptyList())
    val byProductId = loadMovementsFor(products)
    return Stocks(
        products.map { p ->
            Stock(p, StockMovements(byProductId[p.id()] ?: emptyList()))
        },
    )
}
```

import に `Stocks` を追加。

- [ ] **Step B-4.10: `StockRpcService.list` を `Stocks` 返却に**

```kotlin
import net.brightroom.mindstock.domain.model.stock.Stocks
// ...
suspend fun list(householdId: HouseholdId): Stocks
```

- [ ] **Step B-4.11: `StockController.list` の戻り値型を更新**

```kotlin
override suspend fun list(householdId: HouseholdId): Stocks =
    tx(database) {
        actor
        val household =
            householdRepository.findById(householdId)
                ?: throw NotFoundException("household not found: $householdId")
        stockService.list(household)
    }
```

import 追加。

- [ ] **Step B-4.12: backend/api integration / e2e を更新**

```bash
grep -rn "stocksOf\|StockRpcService.*list\|stockService.list\b" backend/api/src/test --include="*.kt"
```

`StockDataSourceIntegrationTest.kt`, `StockRpcServiceE2eTest.kt`, `StockControllerTest.kt` で:

- `val stocks: List<Stock> = ...` → `val stocks: Stocks = ...`
- `stocks.size` / `stocks[0]` などの参照を `stocks.list.size` / `stocks.list[0]` に
- `stocks shouldBe listOf(...)` → `stocks.list shouldBe listOf(...)` または `stocks shouldBe Stocks(listOf(...))`

- [ ] **Step B-4.13: ビルドと全テスト**

```bash
./gradlew clean build
```

期待: BUILD SUCCESSFUL、全テスト pass。

- [ ] **Step B-4.14: コミット**

```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor(domain): introduce Stocks aggregate collection

Products / CatalogItems / HouseholdMembers / StockMovements と並ぶ
集合体として Stocks を新設。needsReplenishment フィルタを Stocks 側に
集約し、ShoppingList は Stocks への薄い委譲ラッパに。

StockRepository / StockService / StockRpcService の list 系の戻り値も
List<Stock> から Stocks に統一。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step B-4.15: PR 作成**

```bash
git push -u origin HEAD
gh pr create --title "refactor(domain): introduce Stocks aggregate collection" --body "$(cat <<'EOF'
## Summary
- Stocks 集合体新設 (val list + needsReplenishment() 集計メソッド)
- ShoppingList を Stocks 委譲の薄いラッパに変更
- StockRepository / StockService / StockRpcService の list 系戻り値を Stocks に統一
- Wire 形式: list の戻り値が `List<Stock>` から `Stocks` に (object ラッパ) — frontend 未使用なので影響なし

## Test plan
- [x] domain StocksTest 新規 pass
- [x] `./gradlew clean build` 成功
- [x] backend/api integration/e2e 全 pass

Spec: docs/superpowers/specs/2026-05-29-domain-cohesion-coupling-design.md (§2.2 / §6)
EOF
)"
```

---

## B-5: latestNames aliased subquery 共通化

**目的:** `groupBy(user_id) + max(id)` の latest display name サブクエリが 5 callsite（`StockRegisterDataSource.loadProfile` は B-2 で消滅済）に散在しているのを共通ヘルパに集約。

callsite:
- `UserDataSource.queryLatest`
- `HouseholdDataSource.findOf`
- `HouseholdDataSource.findById`
- `HouseholdRegisterDataSource.create`
- `StockDataSource.movementHistory`
- `StockDataSource.loadMovementsFor`

### Steps

- [ ] **Step B-5.1: TDD — まず共通ヘルパが置換した結果を見るために 1 callsite を移行する形を試作する**

ヘルパファイルを作る前に、既存の `UserDataSource.queryLatest` がどんな型操作をしているかを確認:

```bash
grep -n "alias\[" backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/user/UserDataSource.kt
```

`alias[UserDisplayNamesTable.user_id]` などの `QueryAlias.get<T>(column: Column<T>): Expression<T>` の戻り値型を確認する（Exposed v1 のドキュメント / 既存実装で型推論結果を見る）。

実装ノート: 既存コードでは `val latestUserId = latestNames[UserDisplayNamesTable.user_id]` のように型推論に任せて受けている。`Expression<Uuid>` か `ExpressionWithColumnType<Uuid>` を返すはず。共通ヘルパでは同じ呼び出し方ができるよう、`alias` をそのまま外に出して、利用側で `alias[UserDisplayNamesTable.user_id]` を呼ぶスタイルが最も型問題を避けやすい。

- [ ] **Step B-5.2: `LatestDisplayNames.kt` を作成**

```kotlin
// backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/user/LatestDisplayNames.kt
package net.brightroom.mindstock.infrastructure.datasource.user

import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.QueryAlias
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.select
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * user_id ごとの最新 display name 行を選ぶ aliased subquery。
 *
 * 6 箇所で「`groupBy(user_id) + max(id)` で max display name id を引き、
 * その id で UserDisplayNamesTable を JOIN する」idiom が散在していたため共通化。
 *
 * 使い方:
 * ```
 * val latest = latestDisplayNames()
 * UsersTable
 *   .join(latest.alias, JoinType.INNER, onColumn = UsersTable.id, otherColumn = latest.userId)
 *   .join(UserDisplayNamesTable, JoinType.INNER) {
 *       (UserDisplayNamesTable.user_id eq latest.userId) and
 *           (UserDisplayNamesTable.id eq latest.maxId)
 *   }
 * ```
 */
@OptIn(ExperimentalUuidApi::class)
internal class LatestDisplayNames(
    val alias: QueryAlias,
    val userId: Expression<Uuid>,
    val maxId: Expression<Long>,
)

@OptIn(ExperimentalUuidApi::class)
internal fun latestDisplayNames(): LatestDisplayNames {
    val maxIdExpr = UserDisplayNamesTable.id.max().alias("max_name_id")
    val alias =
        UserDisplayNamesTable
            .select(UserDisplayNamesTable.user_id, maxIdExpr)
            .groupBy(UserDisplayNamesTable.user_id)
            .alias("latest_names")
    return LatestDisplayNames(
        alias = alias,
        userId = alias[UserDisplayNamesTable.user_id],
        maxId = alias[maxIdExpr],
    )
}
```

実装ノート: `alias[col]` の戻り値が `Expression<T>` ではなく `ExpressionWithColumnType<T>` の場合は `Expression<T>` を上位型として受け取れる。コンパイルが通らなければ `Expression` を `ExpressionWithColumnType` に差し替える。

- [ ] **Step B-5.3: `UserDataSource.queryLatest` を共通ヘルパに置換**

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.user

import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.profile.Profile
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class UserDataSource : UserRepository {
    override fun findProfileByAuthIdentity(identity: AuthIdentity): Profile? = queryLatest { UsersTable.zitadel_sub eq identity.subject() }

    override fun findProfileById(id: UserId): Profile? = queryLatest { UsersTable.id eq id() }

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

不要な import (`alias`, `max`, `select`, `UserDisplayNamesTable.*` の一部) を削除。

- [ ] **Step B-5.4: `UserDataSourceIntegrationTest.kt` で pass を確認**

```bash
./gradlew :backend:api:test --tests "*UserDataSourceIntegrationTest*"
```

期待: 既存 pass を維持。

- [ ] **Step B-5.5: `HouseholdDataSource.findOf` を共通ヘルパに置換**

`backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/household/HouseholdDataSource.kt` の `findOf` 冒頭の `maxNameIdAlias` ブロック (line 27-34) を削除し、`val latest = latestDisplayNames()` で置換:

```kotlin
override fun findOf(userId: UserId): Household? {
    val latest = latestDisplayNames()

    // --- target household: most recent active membership's household for this user ---
    val maxMembershipIdAlias = HouseholdMembershipsTable.id.max().alias("max_membership_id")
    val targetHousehold = ... // 既存通り

    val targetHouseholdId = targetHousehold[HouseholdMembershipsTable.household_id]

    val rows =
        HouseholdMembershipsTable
            .join(..., HouseholdMembershipRevocationsTable, ...)
            .join(targetHousehold, JoinType.INNER, onColumn = HouseholdMembershipsTable.household_id, otherColumn = targetHouseholdId)
            .join(UsersTable, JoinType.INNER, onColumn = HouseholdMembershipsTable.user_id, otherColumn = UsersTable.id)
            .join(latest.alias, JoinType.INNER, onColumn = UsersTable.id, otherColumn = latest.userId)
            .join(UserDisplayNamesTable, JoinType.INNER) {
                (UserDisplayNamesTable.user_id eq latest.userId) and
                    (UserDisplayNamesTable.id eq latest.maxId)
            }.selectAll()
            ...
}
```

import を整理。`latestDisplayNames` ヘルパは `infrastructure.datasource.user.latestDisplayNames` を import する。

- [ ] **Step B-5.6: `HouseholdDataSource.findById` も同様に置換**

同ファイルの `findById` メソッドの latest names ブロックを `latest = latestDisplayNames()` で置換。

- [ ] **Step B-5.7: `HouseholdRegisterDataSource.create` を共通ヘルパに置換**

```kotlin
val ownerProfile = run {
    val latest = latestDisplayNames()
    UsersTable
        .join(latest.alias, JoinType.INNER, onColumn = UsersTable.id, otherColumn = latest.userId)
        .join(UserDisplayNamesTable, JoinType.INNER) {
            (UserDisplayNamesTable.user_id eq latest.userId) and
                (UserDisplayNamesTable.id eq latest.maxId)
        }.selectAll()
        .where { UsersTable.id eq ownerId() }
        .single()
        .toProfile()
}
```

import 整理。

- [ ] **Step B-5.8: `StockDataSource.movementHistory` と `loadMovementsFor` を置換**

両関数の冒頭 `maxNameIdAlias` ブロックを `latest = latestDisplayNames()` で置換し、`latestNameUserId` / `latestNameMaxId` 変数を `latest.userId` / `latest.maxId` に書き換え:

```kotlin
override fun movementHistory(product: Product, limit: Int): StockMovements {
    require(limit > 0) { "limit must be > 0" }
    val latest = latestDisplayNames()

    val rows =
        StockMovementsTable
            .join(UsersTable, JoinType.INNER, onColumn = StockMovementsTable.acted_by, otherColumn = UsersTable.id)
            .join(latest.alias, JoinType.INNER, onColumn = UsersTable.id, otherColumn = latest.userId)
            .join(UserDisplayNamesTable, JoinType.INNER) {
                (UserDisplayNamesTable.user_id eq latest.userId) and
                    (UserDisplayNamesTable.id eq latest.maxId)
            }.selectAll()
            ...
}

private fun loadMovementsFor(products: List<Product>): Map<Uuid, List<StockMovement>> {
    if (products.isEmpty()) return emptyMap()
    val productUuids = products.map { it.id() }
    val latest = latestDisplayNames()
    ...
}
```

`alias`, `max`, `select` (Exposed) の import が不要になれば削除。

- [ ] **Step B-5.9: ビルドと全テスト**

```bash
./gradlew clean build
```

期待: BUILD SUCCESSFUL、全 integration / e2e test pass。

注: ヘルパ抽出は SQL クエリプランが完全に同じになることを意図しているため、既存テストが pass すれば動作は等価。

- [ ] **Step B-5.10: コミット**

```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor(infrastructure): extract latestDisplayNames helper

groupBy(user_id) + max(id) で最新 display name 行を引く aliased
subquery が 5 callsite に散在していたため、共通ヘルパ
latestDisplayNames() を infrastructure/datasource/user に抽出。

- UserDataSource.queryLatest
- HouseholdDataSource.findOf, findById
- HouseholdRegisterDataSource.create
- StockDataSource.movementHistory, loadMovementsFor

(StockRegisterDataSource.loadProfile は B-2 で関数ごと削除済み。)

SQL クエリプランは不変。既存 integration test の通過で等価性を担保。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step B-5.11: PR 作成**

```bash
git push -u origin HEAD
gh pr create --title "refactor(infrastructure): extract latestDisplayNames helper" --body "$(cat <<'EOF'
## Summary
- groupBy(user_id) + max(id) idiom を共通ヘルパに抽出 (5 callsite)
- 配置先: backend/core/.../infrastructure/datasource/user/LatestDisplayNames.kt
- Plan A の post-merge follow-up

## Test plan
- [x] `./gradlew clean build` 成功
- [x] UserDataSourceIntegrationTest pass
- [x] HouseholdDataSourceIntegrationTest pass
- [x] HouseholdRegisterDataSourceIntegrationTest pass
- [x] StockDataSourceIntegrationTest pass

Spec: docs/superpowers/specs/2026-05-29-domain-cohesion-coupling-design.md (§6.4)
EOF
)"
```

---

## 検証チェックリスト

Plan B 完了の判定条件:

- [ ] `domain/.../model/stock/movement/StockMovementType.kt` が存在しない
- [ ] `backend/core/.../infrastructure/datasource/stock/StockMovementType.kt` が internal enum として存在
- [ ] `grep "StockMovement.\?\.type\b" --include="*.kt" -r domain` が 0 件
- [ ] `domain/.../model/stock/movement/StockMovement.kt` に `val product` が無い
- [ ] `grep "Replenishment(.*product\|Consumption(.*product" --include="*.kt" -r .` が 0 件
- [ ] `StockRegisterRepository.replenish/consume` の戻り値が `Unit`
- [ ] `StockRpcService.replenish/consume` の戻り値が `Unit`
- [ ] `StockRegisterDataSource.loadProfile` が存在しない
- [ ] `MinimumStock` が `sealed interface`、`MinimumStock.Set` / `MinimumStock.NotSet` が存在
- [ ] `Product.minimumStock: MinimumStock`（non-null）
- [ ] `grep "MinimumStock(\\d" --include="*.kt" -r .` が 0 件（`MinimumStock.Set(n)` への置換完了）
- [ ] `Stock.needsReplenishment` / `Stock.shortage` の本体が `product.minimumStock.{isBelow,shortage}` 委譲の 1 行
- [ ] `domain/.../model/stock/Stocks.kt` が `@Serializable data class` で存在
- [ ] `ShoppingList(stocks: Stocks)` シグネチャ
- [ ] `StockRpcService.list` の戻り値が `Stocks`
- [ ] `backend/core/.../infrastructure/datasource/user/LatestDisplayNames.kt` が存在
- [ ] `grep "UserDisplayNamesTable.id.max().alias(\"max_name_id\")" --include="*.kt" -r backend/core/src/main` が 1 件のみ（ヘルパ内部）
- [ ] `./gradlew clean build` 成功
- [ ] `./gradlew test` 全 pass
- [ ] 5 PR がすべて main にマージ済（または review/merge 待ち）

---

## 想定リスク

| リスク | 対策 |
|---|---|
| B-3 で `@JvmInline value class : sealed interface` がコンパイルできない | Step B-3.1 の Sandbox 事前検証で早期検出。失敗したら `data class Set(val value: Int)` に切り替え（パフォーマンス影響は許容範囲、本 Plan の他 PR は無影響）|
| B-1/B-2/B-3 の wire 形式破壊で frontend が壊れる | frontend には現状 Stock 系の UI が無く、`grep` で `Replenishment` / `Stocks` / `MinimumStock` 参照が無いことを確認済（exclude `build/`）。将来 stock UI を実装する Plan の前段としてむしろ最適なタイミング |
| B-5 の `LatestDisplayNames` で Exposed の `Expression<T>` 型推論が `Column<T>` と一致せず compile しない | データクラスのフィールド型を `Expression<T>` → `Column<T>` → `ExpressionWithColumnType<T>` の順で試す。最後の手段として generics を `*` に逃がして `@Suppress("UNCHECKED_CAST")` で型を固定 |
| polymorphic discriminator の名前衝突（`StockMovement` も `MinimumStock` も `type` キーを使う） | デフォルト discriminator はクラス FQN が値、キー名は `"type"`。両者は別の object graph に出現するため衝突しない（同一オブジェクト内に両方の sealed が直接フィールドで並ぶことはない）。念のため `SerializationRoundTripTest` で両者を round-trip 確認 |
| B-2 で `replenish/consume` 戻り値を `Unit` に変えたとき、controller test が成功応答を assert しているなら壊れる | `StockControllerTest.kt` の該当 assertion を「例外が出ない」「DB 行が増える」「movementHistory が増える」のいずれかに書き換え |
| `setMinimumStock` を `MinimumStock.Set` のみ受け取る契約にしたが、frontend が NotSet を送ろうとした場合 | frontend 未実装なので影響なし。将来「最低在庫の unset」が必要になったら別 API（`clearMinimumStock` 等）で対応する旨を spec §6.4 に明記済 |
