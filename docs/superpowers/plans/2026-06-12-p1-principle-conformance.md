# フェーズ 1: 原則適合スイープ(primitive→VO・型/ファイル規約) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 公開 API の primitive 引数を VO 化し、1 ファイル 1 型・同名型衝突・`!!` の残違反を全層で一掃する(マスタープラン フェーズ 1)。

**Architecture:** 既存アーキテクチャ(presentation → application ← infrastructure / リッチドメイン / 単一 `/api/rpc`)は維持。シグネチャ変更が中心で、波及先はコンパイラが保証する。挙動変更は **1 点のみ**(タスク A の買い物リスト不足数表示。ユーザ承認済み: domain `shortage()` に一元化)。

**Tech Stack:** Kotlin Multiplatform / kotlinx-serialization / kotlinx-rpc / Exposed / Compose Multiplatform(Wasm)

**Branch:** `refactor/p1-principle-conformance`(main 起点・作成済み)

---

## 前提と全体方針

- **安全網**: 既存 domain テスト(`StockStatusTest` / `ArchivabilityTest` / `MinimumStockTest` / 各 Name VO テスト / `ShoppingNeedTest` / `ShoppingListTest` 等 約110関数)+ backend Service テスト + e2e(`SingleEndpointRpcTest`)。シグネチャ変更は「既存テストを緑のまま通す」ことが第一の検証。
- **新規挙動が入るのはタスク A の screen 部分のみ** → そこだけ期待値を明示的に更新。
- **コンパイラ駆動**: RPC シグネチャや型を変えると、実装/テストの全 override・全呼び出しがコンパイルエラーになる。エラーをゼロにするまで追従するのが各タスクの完了条件。
- **検証コマンド**:
  - domain/共有のみ: `./gradlew :domain:test :shared:test :rpc:test`
  - backend: `./gradlew :backend:core:test :backend:api:test`
  - frontend(フルビルドは OOM): `./gradlew :frontend:compileKotlinWasmJs` + `./gradlew :frontend:commonTest`(= `jsTest`/`wasmJsTest` を name-match)
  - 全体: `./gradlew test`(WasmJs ブラウザテスト除く。OOM 回避は memory: local-build-tips)
  - 統合: `./gradlew integrationTest`(要 `mise run up` + `STORAGE_*` 環境変数)
- **コミットメッセージに issue/PR 番号を書かない**(working agreement)。

---

## Pre-flight: baseline 緑を確認

- [ ] **Step 1: 現状ビルドが緑であることを確認(差分の起点を固定)**

Run:
```bash
./gradlew :domain:test :shared:test :rpc:test :backend:core:test :backend:api:test :frontend:compileKotlinWasmJs
```
Expected: 全て BUILD SUCCESSFUL。落ちる場合はフェーズ 1 着手前に原因を切り分ける(本プランの diff と混ざらないように)。

---

## Task A: NetQuantity 化 + 買い物リスト不足数の domain 一元化(1-1 / 1-2 / 1-3)

`StockStatus.of` / `Archivability.of` / `MinimumStock.isBelow` / `MinimumStock.shortage` の `current: Int` を `NetQuantity` 受けに変える。併せて `ShoppingListScreen` の独自不足数計算を domain の `shortage()` に置換する(**挙動変更: 在庫切れ時 `2×min` → `min`。ユーザ承認済み**)。

**Files:**
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/setting/MinimumStock.kt:15-17`
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/StockStatus.kt:12-20`
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/Archivability.kt:11`
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/Stock.kt:30,80`
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/shopping/ui/ShoppingListScreen.kt:295-297`
- Test: `domain/src/commonTest/.../inventory/product/setting/MinimumStockTest.kt`, `.../inventory/stock/StockStatusTest.kt`, `.../inventory/stock/ArchivabilityTest.kt`(既存・引数型更新)

- [ ] **Step 1: `MinimumStock` の述語 2 つを NetQuantity 受けに変更**

`MinimumStock.kt:15-17` を:
```kotlin
    fun isBelow(current: Int): Boolean = current <= value

    fun shortage(current: Int): Int = (value - current).coerceAtLeast(0)
```
↓
```kotlin
    fun isBelow(current: NetQuantity): Boolean = current() <= value

    fun shortage(current: NetQuantity): Int = (value - current()).coerceAtLeast(0)
```
ファイル先頭の import に追加:
```kotlin
import net.brightroom.mindstock.domain.model.inventory.quantity.NetQuantity
```
> 戻り値は `Boolean`/`Int` のまま(述語の Boolean / 不足数の集計値は VO 原則の対象外。`domain-guideline.md`「述語メソッドと区分の判定」)。

- [ ] **Step 2: `StockStatus.of` を NetQuantity 受けに変更**

`StockStatus.kt:12-20` を:
```kotlin
        fun of(
            current: Int,
            minimum: MinimumStock,
        ): StockStatus =
            when {
                current <= 0 -> 在庫切れ
                minimum.isBelow(current) -> 残りわずか
                else -> 十分
            }
```
↓
```kotlin
        fun of(
            current: NetQuantity,
            minimum: MinimumStock,
        ): StockStatus =
            when {
                current() <= 0 -> 在庫切れ
                minimum.isBelow(current) -> 残りわずか
                else -> 十分
            }
```
import 追加: `import net.brightroom.mindstock.domain.model.inventory.quantity.NetQuantity`

- [ ] **Step 3: `Archivability.of` を NetQuantity 受けに変更**

`Archivability.kt:11` を:
```kotlin
        fun of(currentQuantity: Int): Archivability = if (currentQuantity == 0) 可能 else 在庫あり
```
↓
```kotlin
        fun of(currentQuantity: NetQuantity): Archivability = if (currentQuantity() == 0) 可能 else 在庫あり
```
import 追加: `import net.brightroom.mindstock.domain.model.inventory.quantity.NetQuantity`

- [ ] **Step 4: `Stock` 側の二段変換 `()()` を解消**

`Stock.kt:30`:
```kotlin
    fun status(): StockStatus = StockStatus.of(currentQuantity()(), product.setting.minimumStock)
```
↓
```kotlin
    fun status(): StockStatus = StockStatus.of(currentQuantity(), product.setting.minimumStock)
```
`Stock.kt:80`(`archive()` 内):
```kotlin
        if (!Archivability.of(currentQuantity()()).archivable) {
```
↓
```kotlin
        if (!Archivability.of(currentQuantity()).archivable) {
```
> `consume`(:45)・`correct`(:73)の `currentQuantity()() < quantity()` 等はローカルな Int 比較なので変更不要。

- [ ] **Step 5: 既存 domain テストの引数型を NetQuantity に更新**

`MinimumStockTest` / `StockStatusTest` / `ArchivabilityTest` の `isBelow(2)` / `shortage(1)` / `StockStatus.of(0, ...)` / `Archivability.of(0)` 等、`Int` リテラルを渡している箇所を `NetQuantity(...)` に置換する(例: `Archivability.of(0)` → `Archivability.of(NetQuantity(0))`)。`shortage` の期待値(`Int`)・`isBelow` の期待値(`Boolean`)は不変。各テストに `import net.brightroom.mindstock.domain.model.inventory.quantity.NetQuantity` を追加。

- [ ] **Step 6: domain を緑にする**

Run: `./gradlew :domain:test`
Expected: PASS(StockStatus/Archivability/MinimumStock/ShoppingNeed/ShoppingList 系全て緑)

- [ ] **Step 7: `ShoppingListScreen` の独自不足数計算を domain `shortage()` に置換(挙動変更)**

`ShoppingListScreen.kt:294-297`:
```kotlin
    val qty = stock.currentQuantity()()
    val min = stock.product.setting.minimumStock()
    val unit = stock.product.setting.unit()
    val shortage = max(1, min - qty + if (status == StockStatus.在庫切れ) min else 0)
```
↓
```kotlin
    val qty = stock.currentQuantity()()
    val unit = stock.product.setting.unit()
    val shortage = stock.product.setting.minimumStock.shortage(stock.currentQuantity())
```
変更点:
- `val min` を削除(他で未使用 — 同ファイル内の参照は :297 のみ)。
- 独自計算 `max(1, ...)` と `status == StockStatus.在庫切れ`(enum の外部 `==` 比較=tell-don't-ask 違反)を削除し、domain の `shortage(NetQuantity)` に一元化。
- 未使用になった `import kotlin.math.max` を削除(同ファイル内に他 `max` 使用がないことを確認して削除。あれば残す)。
> 表示影響: 在庫切れ時の「あと N 個」が `2×min` → `min`(例: min=3, 在庫0 で 6→3)。残りわずか時は実質不変。PR 説明にこの挙動変更を明記する。

- [ ] **Step 8: frontend をコンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL(`max` / `StockStatus` の未使用 import が残っていれば warning ではなく detekt で落ちうるので除去する)

- [ ] **Step 9: Commit**

```bash
git add domain/src/commonMain domain/src/commonTest frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/shopping/ui/ShoppingListScreen.kt
git commit -m "refactor(domain): 在庫数量の述語を NetQuantity 受けに統一し買い物不足数を domain.shortage に一元化"
```
> 在庫切れ時の不足数表示が 2×min→min に変わる挙動変更を含む(domain ロジックへの一元化)。

---

## Task B: SearchLimit VO(1-4)

`CatalogRpcService.search(name, limit: Int)` の `limit` を範囲制約付き VO に。

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/SearchLimit.kt`
- Create: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/catalog/SearchLimitTest.kt`
- Modify: `rpc/.../rpc/catalog/CatalogRpcService.kt:14-17`
- Modify: `backend/.../presentation/rpc/catalog/CatalogController.kt:18-21`
- Modify: `backend/.../application/service/catalog/CatalogService.kt:18-21`
- Modify: `backend/.../application/repository/catalog/CatalogRepository.kt:11-14`
- Modify: `backend/.../infrastructure/datasource/catalog/CatalogDataSource.kt:23-36`
- Modify: `frontend/.../feature/catalog/data/CatalogRepository.kt:30-33`
- Modify: `frontend/.../feature/catalog/AddProductViewModel.kt:30,33,52`
- Modify(test): `frontend/.../feature/catalog/data/CatalogRepositoryTest.kt`(fake の `search` override 2 箇所), `frontend/.../feature/catalog/AddProductViewModelTest.kt`(stub `search` 型)

- [ ] **Step 1: SearchLimit VO の失敗テストを書く**

Create `SearchLimitTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.catalog

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class SearchLimitTest {
    @Test
    fun `1 と 100 は許容`() {
        SearchLimit(1)() shouldBe 1
        SearchLimit(100)() shouldBe 100
    }

    @Test
    fun `0 以下は IllegalArgumentException`() {
        shouldThrow<IllegalArgumentException> { SearchLimit(0) }
    }

    @Test
    fun `100 超は IllegalArgumentException`() {
        shouldThrow<IllegalArgumentException> { SearchLimit(101) }
    }
}
```

- [ ] **Step 2: 失敗を確認**

Run: `./gradlew :domain:compileTestKotlinMetadata` もしくは `./gradlew :domain:test --tests '*SearchLimitTest*'`
Expected: FAIL(`SearchLimit` 未定義のコンパイルエラー)

- [ ] **Step 3: SearchLimit VO を実装**

Create `SearchLimit.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.catalog

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class SearchLimit(
    private val value: Int,
) {
    init {
        require(value in 1..MAX) { "SearchLimit must be in 1..$MAX: $value" }
    }

    operator fun invoke(): Int = value

    override fun toString(): String = value.toString()

    companion object {
        const val MAX = 100
    }
}
```

- [ ] **Step 4: テスト緑を確認**

Run: `./gradlew :domain:test --tests '*SearchLimitTest*'`
Expected: PASS

- [ ] **Step 5: RPC interface / backend を SearchLimit に波及**

`CatalogRpcService.kt`(import に `SearchLimit` 追加、`limit: Int` → `limit: SearchLimit`):
```kotlin
    suspend fun search(
        name: CatalogItemName,
        limit: SearchLimit,
    ): RpcResult<CatalogItems, RpcError>
```
`CatalogController.kt:18-21`: `limit: Int` → `limit: SearchLimit`(本文 `catalogService.search(name, limit)` は不変)。import 追加。
`CatalogService.kt:18-21`: `limit: Int` → `limit: SearchLimit`(本文不変)。import 追加。
`CatalogRepository.kt`(application interface):
```kotlin
    fun search(
        name: CatalogItemName,
        limit: SearchLimit,
    ): CatalogItems
```
import 追加。
`CatalogDataSource.kt:23-36`: シグネチャ `limit: SearchLimit` に変更し、`.limit(limit)` → `.limit(limit())`(`Int` を渡す)。import 追加。

- [ ] **Step 6: frontend を SearchLimit に波及**

`feature/catalog/data/CatalogRepository.kt:30-33`:
```kotlin
    suspend fun search(
        name: CatalogItemName,
        limit: SearchLimit,
    ): RpcOutcome<CatalogItems> = catalogService().search(name, limit).toOutcome()
```
import 追加: `import net.brightroom.mindstock.domain.model.catalog.SearchLimit`
`AddProductViewModel.kt`:
- :30 `private const val SEARCH_LIMIT = 20` → `private val SEARCH_LIMIT = SearchLimit(20)`(`const` は VO に使えないため `val` に)
- :33 `searchCatalog: suspend (CatalogItemName, Int) -> RpcOutcome<CatalogItems>` → `suspend (CatalogItemName, SearchLimit) -> RpcOutcome<CatalogItems>`
- :52 `searchCatalog(CatalogItemName(q), SEARCH_LIMIT)` は不変(SEARCH_LIMIT が SearchLimit になった)
- import 追加: `import net.brightroom.mindstock.domain.model.catalog.SearchLimit`
> `App.kt:466 searchCatalog = catalogRepository::search` は関数参照なので自動追従(編集不要)。

- [ ] **Step 7: テスト fake / stub を追従(コンパイラ駆動)**

- `frontend/.../feature/catalog/data/CatalogRepositoryTest.kt`: fake `CatalogRpcService` の `override suspend fun search(name, limit: Int)` 2 箇所(:23, :47)を `limit: SearchLimit` に。import 追加。
- `frontend/.../feature/catalog/AddProductViewModelTest.kt`: `search` stub の型(`(CatalogItemName, Int)`)を `(CatalogItemName, SearchLimit)` に。呼び出しで `Int` を渡している箇所があれば `SearchLimit(...)` に。
- backend に `CatalogService` / `CatalogDataSource` を直接呼ぶテストがあれば(`grep -rn '\.search(' backend --include=*.kt`)`SearchLimit(...)` に更新。

- [ ] **Step 8: 全体コンパイル + テスト**

Run: `./gradlew :rpc:test :backend:core:test :backend:api:test :frontend:compileKotlinWasmJs :frontend:jsTest`
Expected: PASS(`search` の Int 呼び出し残があればコンパイルエラーで顕在化 → 潰す)

- [ ] **Step 9: Commit**

```bash
git add domain rpc backend frontend
git commit -m "refactor: カタログ検索の limit を SearchLimit VO(1..100)に置き換え"
```

---

## Task C: Wanted VO(1-5)

`setWanted(wanted: Boolean)` と `ShoppingEntry.manuallyWanted: Boolean` を `Wanted` VO に統一する。Wanted は **domain + RPC 契約**に閉じ、frontend は Repository 境界で Boolean↔Wanted を変換する(UI のトグルイベントは Boolean のまま=VM/UI 無改修)。

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/shopping/Wanted.kt`
- Modify: `domain/.../inventory/shopping/ShoppingEntry.kt:9,11`
- Modify: `domain/.../inventory/shopping/ShoppingNeed.kt:18-26`
- Modify: `rpc/.../rpc/product/ProductRegisterRpcService.kt:57-60`
- Modify: `backend/.../presentation/rpc/product/ProductRegisterController.kt:127-134`
- Modify: `backend/.../application/service/product/ProductRegisterService.kt:133-140`
- Modify: `backend/.../application/repository/product/ProductRegisterRepository.kt:26-29`
- Modify: `backend/.../infrastructure/datasource/product/ProductRegisterDataSource.kt:60-72`
- Modify: `backend/.../application/service/product/ProductService.kt:68`
- Modify: `frontend/.../feature/inventory/data/InventoryRepository.kt:60-63`
- Modify: `frontend/.../feature/inventory/ProductDetailViewModel.kt:58`
- Modify(test): `domain/.../shopping/ShoppingNeedTest.kt`, `domain/.../shopping/ShoppingListTest.kt`, `backend/.../product/ProductServiceTest.kt:75-76`, `frontend/.../inventory/ProductDetailViewModelTest.kt:106,139`, frontend の fake `ProductRegisterRpcService.setWanted` override(あれば)

- [ ] **Step 1: Wanted VO を実装(invariant なしのため TDD なし)**

Create `Wanted.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.shopping

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/** 手動の買い物希望フラグ。`@JvmInline` のため wire 上は素の Boolean に unwrap される。 */
@Serializable
@JvmInline
value class Wanted(
    private val value: Boolean,
) {
    operator fun invoke(): Boolean = value

    override fun toString(): String = value.toString()
}
```

- [ ] **Step 2: ShoppingEntry / ShoppingNeed を Wanted に変更**

`ShoppingEntry.kt`:
```kotlin
@Serializable
data class ShoppingEntry(
    val stock: Stock,
    val manuallyWanted: Wanted,
) {
    fun need(): ShoppingNeed = ShoppingNeed.judge(stock.status(), manuallyWanted)

    fun onList(): Boolean = need().onShoppingList
}
```
import 追加: `import net.brightroom.mindstock.domain.model.inventory.shopping.Wanted` は同パッケージのため不要。
`ShoppingNeed.kt:18-26` の `judge`:
```kotlin
        fun judge(
            status: StockStatus,
            manuallyWanted: Wanted,
        ): ShoppingNeed =
            when {
                status != StockStatus.十分 -> 在庫不足
                manuallyWanted() -> 手動希望
                else -> 不要
            }
```
> `status != StockStatus.十分` は「在庫不足の閾値」を表す内部判定であり、`StockStatus` 区分への述語追加はフェーズ 2 以降の検討対象(本タスクの scope 外)。ここでは現状維持。

- [ ] **Step 3: domain テストを Wanted に追従**

- `ShoppingNeedTest.kt`: `judge(StockStatus.X, manuallyWanted = false)` の 4 箇所を `judge(StockStatus.X, Wanted(false))` / `Wanted(true)` に(named 引数は外す or `manuallyWanted = Wanted(false)`)。
- `ShoppingListTest.kt:47-49,59`: `ShoppingEntry(stock(...), manuallyWanted = false/true)` → `Wanted(false)/Wanted(true)`。
- 両ファイルに `import net.brightroom.mindstock.domain.model.inventory.shopping.Wanted`(同パッケージなら不要)。

- [ ] **Step 4: domain 緑を確認**

Run: `./gradlew :domain:test`
Expected: PASS

- [ ] **Step 5: RPC 契約 + backend を Wanted に波及**

`ProductRegisterRpcService.kt:57-60`:
```kotlin
    suspend fun setWanted(
        productId: ProductId,
        wanted: Wanted,
    ): RpcResult<Unit, RpcError>
```
import 追加: `import net.brightroom.mindstock.domain.model.inventory.shopping.Wanted`
`ProductRegisterController.kt:127-134`: `wanted: Boolean` → `wanted: Wanted`(本文 `productRegisterService.setWanted(productId, wanted, residentId)` 不変)。import 追加。
`ProductRegisterService.kt:133-140`: `wanted: Boolean` → `wanted: Wanted`(本文 `productRegisterRepository.setWanted(productId, wanted)` 不変)。import 追加。
`ProductRegisterRepository.kt:26-29`: `wanted: Boolean` → `wanted: Wanted`。import 追加。
`ProductRegisterDataSource.kt:60-72`: シグネチャを `wanted: Wanted` にし、`it[ProductWantedEventsTable.wanted] = wanted` → `= wanted()`(Boolean 列に unwrap)。import 追加。
`ProductService.kt:68`(shoppingList 構築):
```kotlin
        return ShoppingList(stocks.list.map { ShoppingEntry(it, it.product.id in wantedIds) })
```
↓
```kotlin
        return ShoppingList(stocks.list.map { ShoppingEntry(it, Wanted(it.product.id in wantedIds)) })
```
import 追加: `import net.brightroom.mindstock.domain.model.inventory.shopping.Wanted`

- [ ] **Step 6: backend テストを追従**

`ProductServiceTest.kt:75-76`: `.manuallyWanted shouldBe true/false` → `shouldBe Wanted(true)/Wanted(false)`。import 追加。

- [ ] **Step 7: frontend を Wanted 境界で変換**

`InventoryRepository.kt:60-63`(公開シグネチャは Boolean 維持・本文で wrap):
```kotlin
    suspend fun setWanted(
        productId: ProductId,
        wanted: Boolean,
    ): RpcOutcome<Unit> = productRegisterService().setWanted(productId, Wanted(wanted)).toOutcome()
```
import 追加: `import net.brightroom.mindstock.domain.model.inventory.shopping.Wanted`
`ProductDetailViewModel.kt:58`(ShoppingEntry.manuallyWanted が Wanted になったため unwrap):
```kotlin
                        entry != null -> entry.stock to entry.manuallyWanted()
```
> VM の `setWantedFlag: suspend (ProductId, Boolean)`・`setWanted(..., wanted: Boolean)`・UI コールバックは Boolean のまま **無改修**(`repository::setWanted` の型が Boolean を維持するため)。

- [ ] **Step 8: frontend テストを追従(コンパイラ駆動)**

- `ProductDetailViewModelTest.kt:106,139`: `ShoppingEntry(stock = ..., manuallyWanted = true/false)` → `Wanted(true)/Wanted(false)`。import 追加。
- frontend の fake が `ProductRegisterRpcService` を実装している場合、`override suspend fun setWanted(productId, wanted: Boolean)` を `wanted: Wanted` に追従(`grep -rn 'override suspend fun setWanted' frontend`)。
- VM テスト(`ShoppingListViewModelTest` / `ProductDetailViewModelTest`)の `setWanted` stub は `(ProductId, Boolean)` のまま(VM 境界が Boolean なので変更不要)。

- [ ] **Step 9: 全体テスト**

Run: `./gradlew :domain:test :rpc:test :backend:core:test :backend:api:test :frontend:compileKotlinWasmJs :frontend:jsTest`
Expected: PASS

- [ ] **Step 10: Commit**

```bash
git add domain rpc backend frontend
git commit -m "refactor: 手動希望フラグを Wanted VO に統一(domain/RPC 契約。frontend は Repository 境界で変換)"
```

---

## Task D: AuthIdentity の 1 ファイル 1 型分割(1-6)

`AuthIdentity.kt` に同居する 3 トップレベル型を分割する(`domain-one-class-per-file`)。

**Files:**
- Create: `domain/.../resident/identity/auth/AuthProvider.kt`
- Create: `domain/.../resident/identity/auth/AuthSubject.kt`
- Modify: `domain/.../resident/identity/auth/AuthIdentity.kt`(`AuthIdentity` のみ残す)

- [ ] **Step 1: AuthProvider を独立ファイルに**

Create `AuthProvider.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.resident.identity.auth

import kotlinx.serialization.Serializable

@Serializable
enum class AuthProvider { ZITADEL, }
```

- [ ] **Step 2: AuthSubject を独立ファイルに**

Create `AuthSubject.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.resident.identity.auth

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class AuthSubject(
    private val value: String,
) {
    init {
        require(value.isNotBlank()) { "AuthSubject must not be blank" }
    }

    operator fun invoke(): String = value

    override fun toString(): String = value
}
```

- [ ] **Step 3: AuthIdentity.kt を AuthIdentity だけに**

`AuthIdentity.kt` 全体を:
```kotlin
package net.brightroom.mindstock.domain.model.resident.identity.auth

import kotlinx.serialization.Serializable

@Serializable
data class AuthIdentity(
    val provider: AuthProvider,
    val subject: AuthSubject,
)
```
> 3 型は同一パッケージなので、他ファイルの import(`...auth.AuthProvider` / `...auth.AuthSubject` / `...auth.AuthIdentity`)はパッケージ不変のため **追従不要**。

- [ ] **Step 4: コンパイル + テスト**

Run: `./gradlew :domain:test :backend:core:test :backend:api:test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/resident/identity/auth
git commit -m "refactor(domain): AuthProvider / AuthSubject を 1 ファイル 1 型に分割"
```

---

## Task E: Profile 同名衝突の解消(1-7)

`household.Profile` → `HouseholdProfile` / `resident.profile.Profile` → `ResidentProfile` に改名する。同名 2 型の衝突で各所が `import ... as HouseholdProfile`/`as ResidentProfile` の別名回避を強いられている状態を解消する。**機械的リネーム(約 40 ファイル・大半はテスト)。コンパイラが全波及を保証する。**

**主要(main)ファイル:**
- Rename: `domain/.../household/Profile.kt` → `HouseholdProfile.kt`(型名 `Profile` → `HouseholdProfile`)
- Rename: `domain/.../resident/profile/Profile.kt` → `ResidentProfile.kt`(型名 `Profile` → `ResidentProfile`)
- Modify: `domain/.../household/Household.kt:20,28,111`(`val profile: Profile` / `Profile(name)` ×2 → `HouseholdProfile`)
- Modify: `domain/.../resident/Resident.kt:5,10`(import + `val profile: Profile` → `ResidentProfile`)
- Modify(main infra): `backend/.../infrastructure/datasource/household/HouseholdHydration.kt:8,19`(`Profile(name)` → `HouseholdProfile`)/ `.../resident/ResidentHydration.kt:8,48` / `.../resident/ResidentRegisterDataSource.kt:10,44` / `.../stock/StockDataSource.kt:17,88` / `.../household/HouseholdDataSource.kt:32`(既に `as ResidentProfile` 別名 → 別名を外して直接 `ResidentProfile`)

- [ ] **Step 1: household.Profile を HouseholdProfile に改名**

`git mv` でファイル名変更 + 型名変更:
```bash
git mv domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/Profile.kt \
       domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdProfile.kt
```
`HouseholdProfile.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.household

import kotlinx.serialization.Serializable

@Serializable
data class HouseholdProfile(
    val name: HouseholdName,
)
```

- [ ] **Step 2: resident.profile.Profile を ResidentProfile に改名**

```bash
git mv domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/resident/profile/Profile.kt \
       domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/resident/profile/ResidentProfile.kt
```
`ResidentProfile.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.resident.profile

import kotlinx.serialization.Serializable

@Serializable
data class ResidentProfile(
    val displayName: DisplayName,
)
```

- [ ] **Step 3: domain main の参照を更新**

`Household.kt`: `val profile: Profile` → `val profile: HouseholdProfile`、`Profile(name)`(:28, :111)→ `HouseholdProfile(name)`(同パッケージなので import 不要)。
`Resident.kt`: import を `...resident.profile.ResidentProfile` に、`val profile: Profile` → `val profile: ResidentProfile`。

- [ ] **Step 4: backend main(infra)の参照を更新**

下記 main ファイルの `Profile`(household 系)/ `Profile`(resident 系)を新名に置換し、`import ... .Profile`(または `as ResidentProfile` 別名)を新名直接 import に直す:
- `HouseholdHydration.kt:8,19` → `HouseholdProfile`
- `ResidentHydration.kt:8,48` / `ResidentRegisterDataSource.kt:10,44` → `ResidentProfile`
- `StockDataSource.kt:17,88` → `ResidentProfile`
- `HouseholdDataSource.kt:32`(既存 `as ResidentProfile` 別名)→ 別名を外し `ResidentProfile` 直接 import

- [ ] **Step 5: main をコンパイル**

Run: `./gradlew :domain:compileKotlinMetadata :backend:core:compileKotlin`
Expected: BUILD SUCCESSFUL(main 参照の取りこぼしはここで全て顕在化)

- [ ] **Step 6: 全テストの参照を更新(コンパイラ駆動)**

`grep -rln 'household.Profile\|profile.Profile\|model.household.Profile\| Profile(' --include='*.kt' domain backend frontend rpc | grep -v /build/` を worklist にし、各テストで:
- `import ...household.Profile`(または `as HouseholdProfile`)→ `import ...household.HouseholdProfile`、使用箇所 `Profile(HouseholdName(...))` → `HouseholdProfile(HouseholdName(...))`
- `import ...resident.profile.Profile`(または `as ResidentProfile`)→ `import ...resident.profile.ResidentProfile`、使用箇所 `Profile(DisplayName(...))` → `ResidentProfile(DisplayName(...))`
- 既に `as HouseholdProfile`/`as ResidentProfile` で受けているテスト(`AuthViewModelTest` 等)は別名を外して直接 import に統一

対象テスト群(grep 既出): frontend `AuthViewModelSwitchTest` / `AuthViewModelTest` / `OwnershipTest` / `ResidentRepositoryTest` / `ProductDetailViewModelTest` / `OnboardingViewModelTest` / `NeedHouseholdViewModelTest` / `SettingsViewModelTest` / `HouseholdRepositoryTest`、backend `JoinHouseholdScenarioTest` / `CreateInvitationScenarioTest` / `ProductServiceTest` / `ProductRegisterServiceTest` / `ProductRegisterServiceUploadImageTest` / `StockServiceTest` / `StockRegisterServiceTest` / `MindstockAuthPluginTest` / `SingleEndpointRpcTest` / `ResidentRegisterControllerTest` / `StockControllerTest` / `HouseholdRegisterControllerTest` / `HouseholdControllerTest`、rpc `RpcResultSerializationTest` / `ActivityFeedSerializationTest`、domain `ShoppingListTest` / `StockTest` / `StockLatestMovementTest` / `StockForecastTest` / `StockMovementsTest` / `HouseholdRenameTest` / `HouseholdMembershipTest` / `MembersTest` / `HouseholdRequireCanManageTest` / `OwnerChangeabilityTest`。
> 注: `AppShell.kt:29 Profile(...)` は frontend の **Tab enum の `Profile`**(別物)なので変更しない。

- [ ] **Step 7: 全体テスト**

Run: `./gradlew :domain:test :rpc:test :backend:core:test :backend:api:test :frontend:compileKotlinWasmJs :frontend:jsTest`
Expected: PASS(未追従の `Profile(` 参照はコンパイルエラーで顕在化 → 潰す)

- [ ] **Step 8: 残存参照ゼロを確認**

Run:
```bash
grep -rn 'model.household.Profile\b\|resident.profile.Profile\b' --include='*.kt' . | grep -v /build/
```
Expected: 出力ゼロ(`AppShell` の Tab `Profile` は FQCN ではないのでヒットしない)

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "refactor(domain): 同名 Profile を HouseholdProfile / ResidentProfile に改名し別名 import を解消"
```

---

## Task F: テキスト VO のバリデーション重複集約(1-8)

5 つのテキスト VO(`DisplayName` / `HouseholdName` / `ProductName` / `CatalogItemName` / `ProductUnit`)が持つ同一 `require(...)` を `:domain` 内 internal 拡張に集約する。

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/support/StringValidation.kt`
- Modify: 上記 5 VO の `init { ... }`
- Test: 既存 `HouseholdNameTest` / `DisplayNameTest` / `CatalogItemNameTest` / `ProductNameTest` / `ProductUnitTest` がそのまま緑であること(挙動不変)

- [ ] **Step 1: internal 拡張を作成**

Create `StringValidation.kt`:
```kotlin
package net.brightroom.mindstock.domain.support

/**
 * trim 済みで 1..[max] 文字であることを検証する(テキスト VO 共通)。
 * [label] は失敗メッセージに使う型名(例: "DisplayName")。
 */
internal fun String.requireTrimmedWithin(
    max: Int,
    label: String,
) {
    require(isNotEmpty() && length <= max && this == trim()) {
        "$label must be 1..$max chars after trim"
    }
}
```

- [ ] **Step 2: 5 VO の init を置換**

各 VO の `init { require(value.isNotEmpty() && value.length <= MAX_LENGTH && value == value.trim()) { "<Name> must be 1..$MAX_LENGTH chars after trim" } }` を:
```kotlin
    init {
        value.requireTrimmedWithin(MAX_LENGTH, "<Name>")
    }
```
に置換し、各ファイルに `import net.brightroom.mindstock.domain.support.requireTrimmedWithin` を追加する。`<Name>` は順に `DisplayName` / `HouseholdName` / `ProductName` / `CatalogItemName` / `ProductUnit`。`companion object { const val MAX_LENGTH = ... ; operator fun invoke(raw) = X(raw.trim()) }` は不変。

- [ ] **Step 3: 既存 VO テストが緑(挙動不変)**

Run: `./gradlew :domain:test`
Expected: PASS(5 VO のバリデーションは内容不変のため既存テストがそのまま通る)

- [ ] **Step 4: Commit**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/support domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model
git commit -m "refactor(domain): テキスト VO の trim+length 検証を String.requireTrimmedWithin に集約"
```

---

## Task G: StockHydration の `!!` 除去(1-9)

`StockHydration.kt:39-40` の Correction 分岐の `!!` ×2 を、データ不整合を表す明示例外に置換する。

**Files:**
- Modify: `backend/.../infrastructure/datasource/stock/StockHydration.kt:33-41`

- [ ] **Step 1: `!!` を `?: error(...)` に置換**

`StockHydration.kt` の Correction 分岐:
```kotlin
        MovementKind.CORRECTION -> {
            StockMovement.Correction(
                identity,
                quantity,
                occurredAt,
                actor,
                note,
                target = MovementId(this[StockMovementsTable.targetMovementId]!!),
                reason = Reason(this[StockMovementsTable.reason]!!),
            )
        }
```
↓
```kotlin
        MovementKind.CORRECTION -> {
            val movementId = this[StockMovementsTable.id]
            StockMovement.Correction(
                identity,
                quantity,
                occurredAt,
                actor,
                note,
                target =
                    MovementId(
                        this[StockMovementsTable.targetMovementId]
                            ?: error("corrupted correction movement $movementId: target_movement_id is null"),
                    ),
                reason =
                    Reason(
                        this[StockMovementsTable.reason]
                            ?: error("corrupted correction movement $movementId: reason is null"),
                    ),
            )
        }
```
> CORRECTION 行で target/reason が null なのは DB 不整合(あってはならない)。`error()`(`IllegalStateException`)で即死させ、`SessionGuard` の翻訳マップ未掲載 → `RpcError.Internal`(構造化ログ)に落ちる。これは「不在」ではないので `ResourceNotFoundException` は使わない。

- [ ] **Step 2: コンパイル + 既存テスト**

Run: `./gradlew :backend:core:test`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/stock/StockHydration.kt
git commit -m "refactor(infra): StockHydration の Correction 分岐の !! を不整合明示例外に置換"
```

---

## Task H: Json 設定の明示化(1-10)

`KrpcJson` に `namingStrategy = null` を明示し(kRPC 内部 `KrpcMessage` 型への SnakeCase 巻き込みを防ぐ)、`CustomJson` を `prettyPrint = false` にする。

**Files:**
- Modify: `shared/src/commonMain/kotlin/net/brightroom/mindstock/extensions/kotlinx/serialization/Json.kt`

- [ ] **Step 1: CustomJson を prettyPrint=false、KrpcJson に namingStrategy=null**

`Json.kt`:
```kotlin
val CustomJson =
    Json {
        prettyPrint = true
        ...
    }
```
の `prettyPrint = true` → `prettyPrint = false`。
`KrpcJson`:
```kotlin
val KrpcJson =
    Json(from = CustomJson) {
        classDiscriminatorMode = ClassDiscriminatorMode.POLYMORPHIC
    }
```
↓
```kotlin
val KrpcJson =
    Json(from = CustomJson) {
        classDiscriminatorMode = ClassDiscriminatorMode.POLYMORPHIC
        namingStrategy = null
    }
```
> **リスク注**: `namingStrategy = null` で RPC ペイロードの命名が SnakeCase → 既定(プロパティ名そのまま)に変わる。frontend / backend は双方 `KrpcJson` を使う対称な live プロトコル(永続化なし)なので内部整合は保たれる。`SingleEndpointRpcTest`(e2e・実シリアライズ往復)が安全網。

- [ ] **Step 2: シリアライズ round-trip テスト + e2e**

Run: `./gradlew :rpc:test :shared:test :backend:api:test`
Expected: PASS(`RpcResultSerializationTest` / `ActivityFeedSerializationTest` / `SingleEndpointRpcTest` 緑)。落ちたら `namingStrategy` 変更が wire を壊した兆候 → 切り戻して原因調査。

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/net/brightroom/mindstock/extensions/kotlinx/serialization/Json.kt
git commit -m "refactor(shared): KrpcJson に namingStrategy=null を明示し CustomJson を prettyPrint=false に"
```

---

## Task I: RPC 命名統一(1-11)

`registerDisplayName` → `register` に改名する。`imageUrl` は **改名しない**(`shoppingList` / `history` と同じ「名詞クエリ」慣行に一致。KDoc が用途を明示済み)— 命名統一の対象は `registerDisplayName` のみとする(decisive: noun クエリは既存慣行)。

**Files:**
- Modify: `rpc/.../rpc/resident/ResidentRegisterRpcService.kt:12`
- Modify: `backend/.../presentation/rpc/resident/ResidentRegisterController.kt:17`
- Modify: `frontend/.../feature/resident/data/ResidentRepository.kt:14`
- Modify(test): `frontend/.../feature/resident/data/ResidentRepositoryTest.kt:18`, `backend/.../e2e/rpc/SingleEndpointRpcTest.kt:161`, `backend/.../presentation/rpc/resident/ResidentRegisterControllerTest.kt:36,42,45,49`

- [ ] **Step 1: RPC interface のメソッド名を register に**

`ResidentRegisterRpcService.kt:12`:
```kotlin
    suspend fun registerDisplayName(displayName: DisplayName): RpcResult<Resident, RpcError>
```
↓
```kotlin
    /** 初回:表示名を登録する(UC2)。AuthIdentity は session 由来(引数で受けない)。 */
    suspend fun register(displayName: DisplayName): RpcResult<Resident, RpcError>
```

- [ ] **Step 2: Controller の override を追従**

`ResidentRegisterController.kt:17`: `override suspend fun registerDisplayName(...)` → `override suspend fun register(...)`(本文不変)。

- [ ] **Step 3: frontend Repository の呼び出しを追従**

`ResidentRepository.kt:14`: `residentRegisterService().registerDisplayName(displayName)` → `residentRegisterService().register(displayName)`。
> frontend 公開メソッド `ResidentRepository.register(...)` は名称不変(内部 RPC 呼びだけ追従)。`OnboardingViewModel` / `App.kt:193 registerDisplayName = residentRepository::register` も無改修(repository 側の関数名 `register` は不変)。

- [ ] **Step 4: テスト fake / e2e を追従(コンパイラ駆動)**

- `ResidentRepositoryTest.kt:18`: fake の `override suspend fun registerDisplayName(...)` → `register(...)`。
- `SingleEndpointRpcTest.kt:161`: `registerSvc.registerDisplayName(...)` → `registerSvc.register(...)`(:157 のテスト名 string は任意で文言調整)。
- `ResidentRegisterControllerTest.kt:42,49`: `controller.registerDisplayName(...)` → `controller.register(...)`(:36,45 のテスト名 string は任意で文言調整)。

- [ ] **Step 5: 全体テスト + 残参照ゼロ**

Run: `./gradlew :rpc:test :backend:api:test :frontend:compileKotlinWasmJs :frontend:jsTest`
Then: `grep -rn 'registerDisplayName' --include='*.kt' . | grep -v /build/`
Expected: テスト PASS / grep 出力ゼロ

- [ ] **Step 6: Commit**

```bash
git add rpc backend frontend
git commit -m "refactor(rpc): ResidentRegister の registerDisplayName を register に改名"
```

---

## 最終検証

- [ ] **Step 1: 全モジュールのテスト**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL(WasmJs ブラウザテストは除外。OOM 回避は memory: local-build-tips)

- [ ] **Step 2: frontend wasm コンパイル**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 統合テスト(要 DB / Garage)**

Run: `mise run up` 後に `./gradlew integrationTest`
Expected: PASS(Json/命名/シグネチャ変更が実 DB 往復で壊れていないこと)

- [ ] **Step 4: 削除シンボルの残参照ゼロ**

Run:
```bash
grep -rn 'registerDisplayName\|model.household.Profile\b\|resident.profile.Profile\b' --include='*.kt' . | grep -v /build/
```
Expected: 出力ゼロ

- [ ] **Step 5: PR 作成**

PR 説明に必ず記載:
- フェーズ 1 = マスタープランの primitive→VO / 1ファイル1型 / `!!` 除去のスイープ。
- **挙動変更 1 件**: 買い物リストの「あと N 個」表示を domain `shortage()` に一元化。在庫切れ時 `2×min` → `min`(例 6→3)に変わる。
- Json `namingStrategy=null` による RPC wire 命名の変更(frontend/backend 双方 KrpcJson で対称・e2e/統合テストで非退行確認済み)。

---

## Self-Review(spec 突き合わせ)

| マスタープラン タスク | 対応 | 備考 |
|---|---|---|
| 1-1 StockStatus.of NetQuantity | Task A Step 2,4 | `()()` 解消 |
| 1-2 Archivability.of NetQuantity | Task A Step 3,4 | |
| 1-3 MinimumStock + Screen 一元化 | Task A Step 1,7 | **挙動変更**(ユーザ承認: domain shortage に寄せる) |
| 1-4 SearchLimit VO | Task B | range 1..100 / 7 ファイル波及 |
| 1-5 Wanted VO | Task C | domain+RPC 契約に閉じ frontend は境界変換 |
| 1-6 AuthIdentity 分割 | Task D | |
| 1-7 Profile 改名 | Task E | 約 40 ファイル(大半テスト)・コンパイラ駆動 |
| 1-8 テキスト VO 集約 | Task F | requireTrimmedWithin |
| 1-9 StockHydration !! | Task G | error() で不整合明示 |
| 1-10 Json 明示 | Task H | namingStrategy=null / prettyPrint=false |
| 1-11 命名統一 | Task I | registerDisplayName→register(imageUrl は名詞クエリ慣行で据置=明示判断) |

**規模補正**: マスタープランの「約20ファイル」見積りに対し、Profile 改名(~40)+ SearchLimit/Wanted 波及(~20)でテスト含め実数は **約 60 ファイル**(うち大半が import/呼び出しの機械的追従)。正味ロジック変更は小さい。

**リスク**: (1) Task H の wire 命名変更 → e2e/統合テストで担保。(2) Task A の表示挙動変更 → ユーザ承認済み・PR 明記。(3) Task E の改名取りこぼし → コンパイル + grep ゼロ確認で担保。
