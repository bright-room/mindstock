# Plan B: Stock/Movement 整合性 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stock 集約周辺の型整合性を 4 つの独立変更で改善する: (1) `StockMovement.product` 二重保持を解消、(2) `Stocks` 集合体を新設、(3) `StockMovementType` enum と `type` フィールドを削除、(4) `MinimumStock` を sealed ポリモフィック化。

**Architecture:** 各変更は独立 PR として merge 可能。順序は影響範囲の小さい順（Phase 1 → 4）。Phase 4 だけは事前検証（`@JvmInline value class` の `sealed interface` 実装可否、polymorphic serialization の wire 動作）が必要。

**Tech Stack:** Kotlin Multiplatform / Exposed v1 / kotlinx-rpc 0.10.2 / kotlinx-serialization / Kotest

**Spec:** `docs/superpowers/specs/2026-05-29-domain-cohesion-coupling-design.md`（所見 2.1 / 2.2 / 4.5 / 4.6 / 4.7）

**前提:** Plan A が完了済（`User` → `Profile` への切り替えが反映されたコードを前提とする）。本 Plan は internal refactor なので wire 形式互換性の維持は不要。

---

## File Plan

### Phase 1: `MinimumStock` ポリモフィック化（所見 4.6 + 4.7）

**新規/変更:**
- Modify: `domain/.../model/product/MinimumStock.kt` — `sealed interface` 化、`Set` / `NotSet`
- Modify: `domain/.../model/product/Product.kt` — `minimumStock: MinimumStock?` → `minimumStock: MinimumStock`（non-null）
- Modify: `domain/.../model/stock/Stock.kt` — `needsReplenishment()` / `shortage()` のロジックを `MinimumStock` に委譲
- Modify: `domain/src/commonTest/.../model/product/MinimumStockTest.kt` — `NotSet` / `Set` のテスト
- Modify: `domain/src/commonTest/.../model/stock/StockTest.kt` — 構築箇所を `MinimumStock.Set(n)` / `MinimumStock.NotSet` に
- Modify: `domain/src/commonTest/.../model/product/ProductsTest.kt` — 同上
- Modify: `domain/src/commonTest/.../model/shopping/ShoppingListTest.kt` — 同上
- Modify: `domain/src/commonTest/.../model/SerializationRoundTripTest.kt` — sealed polymorphic serialization テスト
- Modify: `backend/core/.../infrastructure/datasource/product/ProductHydration.kt` — `minimumStock?.let { MinimumStock(it) }` → `if (minimumStock != null) MinimumStock.Set(minimumStock) else MinimumStock.NotSet`
- Modify: `backend/core/.../application/repository/product/ProductRegisterRepository.kt` — `setMinimumStock(... value: MinimumStock)` の型は変わらないが、コール元が `MinimumStock.Set(n)` を渡すように
- Modify: `backend/core/.../application/service/product/ProductRegisterService.kt` — 同上
- Modify: `backend/core/.../infrastructure/datasource/product/ProductRegisterDataSource.kt` — `value()` の取り出しを `(value as MinimumStock.Set)()` に変更（書き込みは `Set` のみ受ける契約）
- Modify: `rpc/.../ProductRpcService.kt` — `setMinimumStock` の引数型確認
- Modify: `backend/api/.../presentation/rpc/product/ProductController.kt` — 同上
- Modify: 各 integration test — `MinimumStock(2)` → `MinimumStock.Set(2)` への置換

### Phase 2: `StockMovementType` 廃止（所見 4.5）

**変更:**
- Delete: `domain/.../model/stock/movement/StockMovementType.kt`
- Modify: `domain/.../model/stock/movement/StockMovement.kt` — `val type: StockMovementType` 削除
- Modify: `domain/.../model/stock/movement/Replenishment.kt` — `@Transient val type` 削除
- Modify: `domain/.../model/stock/movement/Consumption.kt` — 同上
- Create: `backend/core/.../infrastructure/datasource/stock/StockMovementType.kt` — infrastructure 層の DB 列 enum として移設（同一名で OK）
- Modify: `backend/core/.../infrastructure/datasource/stock/StockMovementsTable.kt` — `import` を新位置に
- Modify: `backend/core/.../infrastructure/datasource/stock/StockDataSource.kt` — `toStockMovement` 内で sealed pattern match に切り替え（既に `when (type) { REPLENISHMENT -> ... ; CONSUMPTION -> ... }` だが、type を Kotlin enum で受けて DB から取り出すコードのみ infrastructure 層に保つ）
- Modify: `backend/core/.../infrastructure/datasource/stock/StockHydration.kt` — `toStockMovement` のシグネチャ更新
- Modify: `backend/core/.../infrastructure/datasource/stock/StockRegisterDataSource.kt` — infrastructure の `StockMovementType` を使うように
- Modify: `domain/src/commonTest/.../model/stock/movement/StockMovementsTest.kt` — `m.type` への参照があれば `m is Replenishment` 等に
- Modify: `domain/src/commonTest/.../model/SerializationRoundTripTest.kt` — `type` フィールドが消えるので wire 形式期待値を更新

### Phase 3: `StockMovement.product` 削除（所見 2.1）

**変更:**
- Modify: `domain/.../model/stock/movement/StockMovement.kt` — `val product: Product` 削除
- Modify: `domain/.../model/stock/movement/Replenishment.kt` — `product` 削除
- Modify: `domain/.../model/stock/movement/Consumption.kt` — `product` 削除
- Modify: `backend/core/.../infrastructure/datasource/stock/StockHydration.kt` — `toStockMovement(product, actor, ...)` から `product` 引数を残しつつ、戻り値の `Replenishment/Consumption` 構築で product を渡さない
- Modify: `backend/core/.../infrastructure/datasource/stock/StockRegisterDataSource.kt` — `Replenishment(product, ...)` → `Replenishment(quantity, occurredAt, by, note)` などコンストラクタ呼び出しを更新
- Modify: domain test 群: `Replenishment(...)` 構築箇所の引数から `product` を外す
  - `StockMovementsTest.kt`
  - `ShoppingListTest.kt`
  - `StockTest.kt`
  - `SerializationRoundTripTest.kt`
- Modify: `backend/api/.../e2e/stock/StockRpcServiceE2eTest.kt` — 同上
- Modify: `backend/api/.../infrastructure/datasource/repository/stock/StockDataSourceIntegrationTest.kt` — 同上
- Modify: `rpc/.../StockRpcService.kt` — `replenish/consume` の戻り値 wire 形式から `product` フィールドが消える（クライアント側の対応も必要）
- Modify: frontend — `Replenishment` / `Consumption` から `product` を取得している箇所があれば、`Stock.product` 経由に変更

### Phase 4: `Stocks` 集合体新設（所見 2.2）

**新規/変更:**
- Create: `domain/.../model/stock/Stocks.kt`
- Modify: `domain/.../model/shopping/ShoppingList.kt` — `stocks: List<Stock>` → `stocks: Stocks`
- Modify: `domain/.../model/shopping/ShoppingListItem.kt` — 変更なし
- Modify: `backend/core/.../application/repository/stock/StockRepository.kt` — `stocksOf(household): List<Stock>` → `stocksOf(household): Stocks`
- Modify: `backend/core/.../application/service/stock/StockService.kt` — `list(household): List<Stock>` → `list(household): Stocks`
- Modify: `backend/core/.../infrastructure/datasource/stock/StockDataSource.kt` — `stocksOf` 戻り値型
- Modify: `rpc/.../StockRpcService.kt` — `list(householdId): List<Stock>` → `list(householdId): Stocks`
- Modify: `backend/api/.../presentation/rpc/stock/StockController.kt` — 戻り値型
- Modify: `domain/src/commonTest/.../model/shopping/ShoppingListTest.kt` — `ShoppingList(stocks: Stocks)` で構築
- Modify: 各テストで `stocksOf` の戻り値型を扱う箇所
- Modify: frontend — `Stocks` を受けて `.list` でアクセス

---

## 新規型の正準シグネチャ

### `MinimumStock` (Phase 1)

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
        override fun isBelow(quantity: Int) = false
        override fun shortage(quantity: Int) = 0
    }

    @Serializable
    @JvmInline
    value class Set(
        private val value: Int,
    ) : MinimumStock {
        init {
            require(value >= 0) { "minimum_stock must be >= 0, got $value" }
        }

        override fun isBelow(quantity: Int) = quantity < value
        override fun shortage(quantity: Int) = (value - quantity).coerceAtLeast(0)

        operator fun invoke(): Int = value
    }
}
```

### `Product` (Phase 1 変更後)

```kotlin
@Serializable
data class Product(
    val id: ProductId,
    val catalogItem: CatalogItem,
    val minimumStock: MinimumStock,  // ← non-null
    val archived: Boolean,
)
```

### `Stock` (Phase 1 変更後)

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

### `StockMovement` (Phase 2 + Phase 3 変更後)

```kotlin
// domain/.../model/stock/movement/StockMovement.kt
@Serializable
sealed interface StockMovement {
    val quantity: Quantity
    val occurredAt: OccurredAt
    val actor: Profile
    val note: Note
}

@Serializable
data class Replenishment(
    override val quantity: Quantity,
    override val occurredAt: OccurredAt,
    override val actor: Profile,
    override val note: Note,
) : StockMovement

@Serializable
data class Consumption(
    override val quantity: Quantity,
    override val occurredAt: OccurredAt,
    override val actor: Profile,
    override val note: Note,
) : StockMovement
```

### `Stocks` (Phase 4 新規)

```kotlin
// domain/.../model/stock/Stocks.kt
package net.brightroom.mindstock.domain.model.stock

import kotlinx.serialization.Serializable

@Serializable
data class Stocks(
    val list: List<Stock>,
) {
    fun asList(): List<Stock> = list.toList()
    val size: Int get() = list.size
}
```

注: `asList()` / `size` の去就は Plan C で扱う（4.1）。本 Plan では他の集合型と同じ形を踏襲する。

---

## Phase 1: `MinimumStock` ポリモフィック化

**事前検証:**

1. `@JvmInline value class` が `sealed interface` を実装できるか
2. `@Serializable` polymorphic serialization の wire 形式（type discriminator）が `rpc` / `frontend` 経由で正しく動作するか

### Steps

- [ ] **Step 1.1: 検証用に最小例で `@JvmInline value class` の sealed 実装を試す**

ローカルで `domain/src/commonMain/kotlin/.../Sandbox.kt`（一時ファイル）に書いてビルドし、エラーが出なければ削除:

```kotlin
@kotlinx.serialization.Serializable
sealed interface Foo {
    @kotlinx.serialization.Serializable
    data object Bar : Foo
    @kotlinx.serialization.Serializable
    @kotlin.jvm.JvmInline
    value class Baz(val v: Int) : Foo
}
```

```bash
./gradlew :domain:build
```

期待: BUILD SUCCESSFUL。失敗したら本 Plan の Phase 1 は中断、所見 4.6 を見直し（`data class Set(val value: Int)` で代替 = non-value-class）。

ファイル削除:
```bash
rm domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/Sandbox.kt
```

- [ ] **Step 1.2: `MinimumStock.kt` を sealed interface に書き換え**

「新規型の正準シグネチャ」セクションの `MinimumStock` をそのまま書き出す。

- [ ] **Step 1.3: `Product.kt` の `minimumStock` を non-null に**

```kotlin
@Serializable
data class Product(
    val id: ProductId,
    val catalogItem: CatalogItem,
    val minimumStock: MinimumStock,
    val archived: Boolean,
)
```

- [ ] **Step 1.4: `Stock.kt` の `needsReplenishment` / `shortage` を委譲形に**

「新規型の正準シグネチャ」セクションの `Stock` の通り書き換える。

- [ ] **Step 1.5: `ProductHydration.kt` を `Set` / `NotSet` に分岐**

```kotlin
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
        minimumStock = minimumStock?.let { MinimumStock.Set(it) } ?: MinimumStock.NotSet,
        archived = archived,
    )
```

- [ ] **Step 1.6: `ProductRegisterDataSource.kt` の `setMinimumStock` を更新**

書き込み側は `Set` のみ受け取る契約とする（`NotSet` を書き込む意味はない、別 API で「unset」を表現する場合は別途）:

```kotlin
override fun setMinimumStock(
    product: Product,
    value: MinimumStock.Set,   // ← Set のみ受け取る
    editedBy: UserId,
) {
    ProductMinimumStocksTable.insert {
        it[product_id] = product.id()
        it[minimum_stock] = value()
        it[edited_by] = editedBy()
    }
}
```

`ProductRegisterRepository.kt` のインタフェースも `value: MinimumStock.Set` に変更。`ProductRegisterService.kt` も追従。

注: 「unset」を表現する API が必要なら別 Plan で追加。本 Plan の範囲外。

- [ ] **Step 1.7: 各テストの `MinimumStock(n)` 構築を `MinimumStock.Set(n)` に置換**

確認コマンド:
```bash
grep -rn "MinimumStock(" --include="*.kt" .
```

対象:
- `MinimumStockTest.kt`: テストを `MinimumStock.Set(0)` / `MinimumStock.Set(-1)` 等に書き換え、`NotSet` のテストも追加（`isBelow(any)=false`, `shortage(any)=0`）
- `StockTest.kt`: `Product(..., minimumStock = MinimumStock.Set(3), ...)` または `MinimumStock.NotSet`
- `ProductsTest.kt`: 同上
- `ShoppingListTest.kt`: 同上
- `SerializationRoundTripTest.kt`: `MinimumStock.Set(3)` / `MinimumStock.NotSet` のラウンドトリップを両方確認
- `ProductRegisterDataSourceIntegrationTest.kt`: `MinimumStock(2)` → `MinimumStock.Set(2)`
- `ProductDataSourceIntegrationTest.kt`: 必要なら同上
- `ProductRpcServiceE2eTest.kt`: 同上

- [ ] **Step 1.8: ビルドと全テスト**

```bash
./gradlew clean build
```

期待: BUILD SUCCESSFUL、全テスト pass。

- [ ] **Step 1.9: 動作確認（任意・推奨）**

ローカル backend を起動し、frontend で最低在庫が未設定の Product を表示・最低在庫を設定する操作を試す。polymorphic JSON が正しく往復することを目視確認。

- [ ] **Step 1.10: コミット**

```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor(domain): make MinimumStock polymorphic (Set/NotSet)

MinimumStock を sealed interface に変え、null で「未設定」を表現するの
ではなくドメイン型で表現する。Stock 側のロジックを MinimumStock 側に
委譲し、needsReplenishment / shortage の重複ロジックを解消。

書き込み API は MinimumStock.Set のみ受け取る契約に。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 2: `StockMovementType` 廃止

**目的:** sealed の網羅判別で型情報が取れる以上、`StockMovementType` enum と `StockMovement.type` フィールドは冗長。infrastructure 層は DB の `enumerationByName` に依然として Kotlin enum が必要なので、enum 自体は **infrastructure 層に移設** する。

### Steps

- [ ] **Step 2.1: infrastructure 層に `StockMovementType.kt` を新規作成**

```kotlin
// backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/stock/StockMovementType.kt
package net.brightroom.mindstock.infrastructure.datasource.stock

internal enum class StockMovementType {
    REPLENISHMENT,
    CONSUMPTION,
}
```

注: `@Serializable` 不要（infrastructure 内でのみ使う）。

- [ ] **Step 2.2: domain の `StockMovementType.kt` を削除**

```bash
git rm domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/movement/StockMovementType.kt
```

- [ ] **Step 2.3: domain の `StockMovement.kt` から `type` 削除**

```kotlin
@Serializable
sealed interface StockMovement {
    val product: Product        // ← Phase 3 で削除
    val quantity: Quantity
    val occurredAt: OccurredAt
    val actor: Profile
    val note: Note
    // type 削除
}
```

`Replenishment.kt` / `Consumption.kt` も `@Transient override val type` 行を削除。`import kotlinx.serialization.Transient` も不要なら削除。

- [ ] **Step 2.4: `StockMovementsTable.kt` の import を新位置に**

```kotlin
import net.brightroom.mindstock.infrastructure.datasource.stock.StockMovementType
```

- [ ] **Step 2.5: `StockHydration.kt` の `toStockMovement` シグネチャを infrastructure enum で受ける**

```kotlin
internal fun toStockMovement(
    product: Product,
    actor: Profile,
    type: StockMovementType,   // ← infrastructure 層の enum
    quantity: Int,
    occurredAt: Instant,
    note: String,
): StockMovement { ... }
```

`when (type)` の各 branch は変わらない。

- [ ] **Step 2.6: `StockDataSource.kt` の呼び出し側を確認**

`row[StockMovementsTable.type]` は infrastructure 層の enum を返すので変更不要。

- [ ] **Step 2.7: `StockRegisterDataSource.kt` の `insertMovement` も infrastructure 層 enum を使用**

`import net.brightroom.mindstock.infrastructure.datasource.stock.StockMovementType` に変更。

- [ ] **Step 2.8: テストで `m.type` への参照を除去**

```bash
grep -rn "\.type" domain/src/commonTest --include="*.kt" | grep -i movement
```

`StockMovementsTest.kt` 等で `m.type == StockMovementType.REPLENISHMENT` のような比較があれば `m is Replenishment` に。

- [ ] **Step 2.9: `SerializationRoundTripTest.kt` の wire 形式期待値を更新**

`type` フィールドが消えるので、JSON 形式チェック箇所があれば修正。`@Serializable sealed` の discriminator (`type` という JSON field 名がデフォルト) と衝突しないことも確認（StockMovement が sealed の discriminator として `type` を使うが、`@JsonClassDiscriminator` を指定しない限りクラス名が入る）。詳細は kotlinx-serialization 仕様に依存。

- [ ] **Step 2.10: ビルドと全テスト**

```bash
./gradlew clean build
```

- [ ] **Step 2.11: コミット**

```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor(domain): remove redundant StockMovementType from domain

sealed StockMovement で網羅判別が可能なため、enum StockMovementType と
val type フィールドを domain から削除。infrastructure 層の DB 列マッピング
には引き続き必要なので、infrastructure パッケージに同名 enum を移設。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 3: `StockMovement.product` 削除

**目的:** `Stock.product` と `StockMovement.product` の二重保持を解消。Stock 集約ルートに product を一元化することで「全 movement の product が一致」という不変条件を構造で表現する。

### Steps

- [ ] **Step 3.1: domain の `StockMovement.kt` から `product` 削除**

```kotlin
@Serializable
sealed interface StockMovement {
    val quantity: Quantity
    val occurredAt: OccurredAt
    val actor: Profile
    val note: Note
}
```

- [ ] **Step 3.2: `Replenishment.kt` と `Consumption.kt` から `product` 削除**

```kotlin
@Serializable
data class Replenishment(
    override val quantity: Quantity,
    override val occurredAt: OccurredAt,
    override val actor: Profile,
    override val note: Note,
) : StockMovement
```

- [ ] **Step 3.3: `StockHydration.kt` の構築箇所から `product` を除く**

```kotlin
internal fun toStockMovement(
    product: Product,   // ← この引数自体は呼び出し側のコンテキストとして残しても良いが、戻り値からは消える
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

注: `product` 引数を完全に削除しても良いが、呼び出し側のコード読解性のため残す選択肢もある。**削除する方を推奨**（不要引数）:

```kotlin
internal fun toStockMovement(
    actor: Profile,
    type: StockMovementType,
    quantity: Int,
    occurredAt: Instant,
    note: String,
): StockMovement { ... }
```

呼び出し側 `StockDataSource.kt` も追従:

```kotlin
toStockMovement(actor = ..., type = ..., quantity = ..., occurredAt = ..., note = ...)
```

- [ ] **Step 3.4: `StockRegisterDataSource.kt` の構築を更新**

```kotlin
override fun replenish(
    product: Product,
    quantity: Quantity,
    occurredAt: OccurredAt,
    by: UserId,
    note: Note,
): Replenishment {
    insertMovement(product, quantity, occurredAt, by, note, StockMovementType.REPLENISHMENT)
    // product は INSERT 用、Replenishment 自体には不要
    val byProfile = TODO_LOAD_PROFILE   // ← Plan A 完了後は actor: UserId なので、戻り値の actor を Profile で組むには別途 lookup が必要
    return Replenishment(quantity, occurredAt, byProfile, note)
}
```

**重要な追加判断**: `StockRegisterRepository.replenish/consume` の戻り値は `Replenishment` / `Consumption` だが、Plan A で `actor: Profile` に変更されている。書き込み API は `UserId` を受けるので、戻り値の Replenishment に Profile を埋めるには追加クエリが必要。

解決オプション:

- **(a) 戻り値を `Unit` または `StockMovementId` に変更**（書き込み後のフル movement 取得は読み込み API に任せる）
- **(b) 戻り値の actor を `Profile` で組むため、書き込み直後に `findProfileById(by)` を呼ぶ**
- **(c) Plan A の決定を見直し、書き込み API も `Profile` を受ける**

**推奨: (a)**。書き込み戻り値で全部詰めて返す必要は薄い。Controller 層で「成功 → クライアントに何を返すか」の判断をする。

実装:

```kotlin
override fun replenish(
    product: Product,
    quantity: Quantity,
    occurredAt: OccurredAt,
    by: UserId,
    note: Note,
) {
    insertMovement(product, quantity, occurredAt, by, note, StockMovementType.REPLENISHMENT)
}
```

`StockRegisterRepository.kt` インタフェースも `Unit` 戻り値に。`StockRegisterService.kt`, `StockController.kt`, `StockRpcService.kt` も追従。RPC レイヤは `Unit` を返すか、新規 movement の id を返すか。**シンプルに `Unit`** で進め、クライアント側は次の `movementHistory` でリフレッシュする方針。

注: この変更は所見 2.1 から外れるが、Phase 3 の整合性確保上必要。spec の Section 5 に追加する形でカバー。

- [ ] **Step 3.5: domain test の `Replenishment(product, ...)` 構築箇所を全て更新**

確認コマンド:
```bash
grep -rn "Replenishment(" --include="*.kt" .
grep -rn "Consumption(" --include="*.kt" .
```

各箇所で `product = ...,` 行を削除。

- [ ] **Step 3.6: backend/api E2E test 更新**

`StockRpcServiceE2eTest.kt`, `StockDataSourceIntegrationTest.kt` で:
- `Replenishment(product = ..., ...)` から `product` 削除
- `replenish` の戻り値が `Unit` になることに合わせて検証ロジック変更（`movementHistory` で取得して assert する形）

- [ ] **Step 3.7: frontend で `Replenishment.product` / `Consumption.product` 参照箇所を更新**

確認コマンド:
```bash
grep -rn "\.product" frontend/src --include="*.kt" | grep -iE "replenishment|consumption|movement"
```

該当箇所は `Stock.product`（外側コンテキスト）から取得する形に変更。

- [ ] **Step 3.8: rpc `StockRpcService.kt` の戻り値型を Unit に**

```kotlin
@Rpc
interface StockRpcService {
    suspend fun get(productId: ProductId): Stock
    suspend fun list(householdId: HouseholdId): List<Stock>   // ← Phase 4 で Stocks に
    suspend fun movementHistory(productId: ProductId, limit: Int): StockMovements
    suspend fun replenish(productId: ProductId, qty: Quantity, occurredAt: OccurredAt, note: Note)
    suspend fun consume(productId: ProductId, qty: Quantity, occurredAt: OccurredAt, note: Note)
}
```

- [ ] **Step 3.9: ビルドと全テスト**

```bash
./gradlew clean build
```

- [ ] **Step 3.10: コミット**

```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor(domain): remove redundant product field from StockMovement

Stock.product と StockMovement.product の二重保持を解消。集約ルートに
product を一元化することで「全 movement の product が Stock.product と
一致」という不変条件を型で表現する。

副次変更: StockRegisterRepository の replenish/consume の戻り値を Unit
に。書き込み後の movement 詳細はクライアントが movementHistory で再取得
する方針。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 4: `Stocks` 集合体新設

**目的:** `Products / CatalogItems / HouseholdMembers / StockMovements` と並ぶ集合体として `Stocks` を導入。`ShoppingList` の引数も `Stocks` に統一。

### Steps

- [ ] **Step 4.1: `Stocks.kt` を新規作成**

```kotlin
// domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/Stocks.kt
package net.brightroom.mindstock.domain.model.stock

import kotlinx.serialization.Serializable

@Serializable
data class Stocks(
    val list: List<Stock>,
) {
    fun asList(): List<Stock> = list.toList()
    val size: Int get() = list.size
}
```

- [ ] **Step 4.2: `ShoppingList.kt` を `Stocks` 受け取りに変更**

```kotlin
// domain/.../model/shopping/ShoppingList.kt
package net.brightroom.mindstock.domain.model.shopping

import net.brightroom.mindstock.domain.model.stock.Stocks

class ShoppingList(
    private val stocks: Stocks,
) {
    fun itemsToBuy(): List<ShoppingListItem> =
        stocks
            .asList()
            .filter { it.needsReplenishment() }
            .map { ShoppingListItem(it, shortage = it.shortage()) }
}
```

- [ ] **Step 4.3: `StockRepository.kt` の戻り値を `Stocks` に**

```kotlin
interface StockRepository {
    fun stockOf(product: Product): Stock
    fun stocksOf(household: Household): Stocks
    fun movementHistory(product: Product, limit: Int = 50): StockMovements
}
```

- [ ] **Step 4.4: `StockDataSource.stocksOf` を `Stocks` 返却に**

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

- [ ] **Step 4.5: `StockService.list` を `Stocks` 返却に**

```kotlin
fun list(household: Household): Stocks = stockRepository.stocksOf(household)
```

- [ ] **Step 4.6: `StockRpcService.list` を `Stocks` 返却に**

```kotlin
suspend fun list(householdId: HouseholdId): Stocks
```

`StockController.kt` も追従。

- [ ] **Step 4.7: domain test `ShoppingListTest.kt` を `Stocks` で構築するように更新**

```kotlin
val stocks = Stocks(listOf(stock1, stock2, stock3))
val shoppingList = ShoppingList(stocks)
```

- [ ] **Step 4.8: backend/api E2E test 更新**

`StockRpcServiceE2eTest.kt` で `list(...)` 戻り値型を `Stocks` で受ける形に。`stocks.list` または `stocks.asList()` でアクセス。

- [ ] **Step 4.9: frontend で `list` の戻り値を扱う箇所**

確認コマンド:
```bash
grep -rn "StockRpcService" frontend/src --include="*.kt"
```

`List<Stock>` を受けていた箇所を `Stocks` に変更、`.list` でアクセス。

- [ ] **Step 4.10: ビルドと全テスト**

```bash
./gradlew clean build
```

- [ ] **Step 4.11: コミット**

```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor(domain): introduce Stocks aggregate collection

Products/CatalogItems/HouseholdMembers/StockMovements と並ぶ集合型として
Stocks を新設。ShoppingList の引数も Stocks に、StockRepository/Service/
Rpc の戻り値も List<Stock> → Stocks に統一。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## 検証チェックリスト

Plan B 完了の判定条件:

- [ ] `domain/.../model/stock/movement/StockMovementType.kt` が存在しない
- [ ] `domain/.../model/stock/Stocks.kt` が存在し、`@Serializable data class` である
- [ ] `MinimumStock` が `sealed interface` で、`MinimumStock.Set` / `MinimumStock.NotSet` が存在
- [ ] `grep "MinimumStock(\d" --include="*.kt" -r .` の結果が 0 件（`MinimumStock.Set(n)` への置換完了）
- [ ] `grep "StockMovement.\?\.type\b" --include="*.kt" -r .` が 0 件
- [ ] `grep "Replenishment(product\|Consumption(product" --include="*.kt" -r .` が 0 件
- [ ] `Stock.needsReplenishment` / `Stock.shortage` の本体が `product.minimumStock.{isBelow,shortage}` への 1 行委譲になっている
- [ ] `./gradlew clean build` 成功
- [ ] `./gradlew test` 全 pass

---

## 想定リスク

| リスク | 対策 |
|---|---|
| Phase 1 で `@JvmInline value class` が `sealed interface` を実装できない | Step 1.1 の事前検証で早期検出。失敗したら `data class Set(val value: Int)` に切り替え |
| polymorphic serialization の type discriminator が `StockMovement` と `MinimumStock` で衝突 | kotlinx-serialization のデフォルトは class FQN discriminator なので衝突なし。`@JsonClassDiscriminator` 指定があれば確認 |
| Phase 3 の `replenish/consume` 戻り値を Unit にする変更がクライアントの期待を破る | 同一 deploy 単位で frontend も更新。クライアントの楽観更新ロジックがあれば `movementHistory` 再取得に切り替え |
| `setMinimumStock` の引数を `MinimumStock.Set` に絞ったことで「unset」が表現できなくなる | 本 Plan の範囲外。必要になったら別 API（例: `clearMinimumStock(product, editedBy)`）を追加 |
