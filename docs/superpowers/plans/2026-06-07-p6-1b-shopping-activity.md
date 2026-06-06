# P6-1b 買い物リスト / 活動タブ・setWanted Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 買い物リストタブ・活動タブを live 配線し、ProductDetail を app 層共有オーバーレイへ昇格して 3 タブから開けるようにし、`setWanted` で買い物リストの入/外を可能にする。

**Architecture:** `shoppingList()`（世帯の全 stock × wanted を返す read-model）を軸に、ProductDetail は `householdId+productId` で Stock と manuallyWanted を一括解決。オーバーレイ上の mutation を裏のタブへ波及させるため、`ReauthController` 同型の `InventoryRefreshController`（`SharedFlow<Unit>`）を導入。collect→reload は Compose 層（各 route の `LaunchedEffect`）で行い、VM は成功時に `request()` のみ（テスト容易・既存「Compose が load を駆動」パターン踏襲）。

**Tech Stack:** Kotlin Multiplatform / Compose Multiplatform (Wasm) / kotlinx-coroutines (StateFlow/SharedFlow) / kotlin.test + Kotest assertions / compose-resources (i18n)。

設計: `docs/superpowers/specs/2026-06-07-p6-1b-shopping-activity-design.md`

---

## File Structure

**新規（core）**
- `frontend/.../core/ui/InventoryRefreshController.kt` — 在庫変更の単一シグナル。

**新規（feature/shopping）**
- `frontend/.../feature/shopping/ShoppingListUiState.kt`
- `frontend/.../feature/shopping/ShoppingListViewModel.kt`
- `frontend/.../feature/shopping/ui/ShoppingListScreen.kt`
- `frontend/.../feature/shopping/ui/AddToListSheet.kt`

**新規（feature/activity）**
- `frontend/.../feature/activity/ActivityGrouping.kt` — 純関数（日付グルーピング）。
- `frontend/.../feature/activity/ActivityUiState.kt`
- `frontend/.../feature/activity/ActivityViewModel.kt`
- `frontend/.../feature/activity/ui/ActivityScreen.kt`

**新規（feature/inventory）**
- `frontend/.../feature/inventory/ui/ProductDetailOverlay.kt` — app から呼ぶオーバーレイ（詳細 + MoveSheet + CorrectionSheet を内包）。`DetailTarget` もここ。

**変更**
- `frontend/.../core/ui/UiText` 周辺は変更なし（再利用）。
- `frontend/.../feature/inventory/data/InventoryRepository.kt` — `shoppingList` / `activity` / `setWanted` 追加、`productRegisterService` DI 追加。
- `frontend/.../feature/inventory/InventoryViewModel.kt` — `refresh` 追加、mutation 成功で `request()`。
- `frontend/.../feature/inventory/ProductDetailUiState.kt` — `Content` に `stock` / `wanted` 追加。
- `frontend/.../feature/inventory/ProductDetailViewModel.kt` — `householdId+productId` 軸へ再設計（shoppingList で Stock+wanted 解決、replenish/consume/correct/setWanted、refresh 発火）。
- `frontend/.../feature/inventory/ui/ProductDetailScreen.kt` — 新 Content 形 + wanted トグル対応（オーバーレイから利用）。
- `frontend/.../feature/inventory/ui/InventoryRoute.kt` — 詳細/シートをオーバーレイへ移し、StockHome は `onOpenProduct` を上げるだけに簡素化。
- `frontend/.../app/shell/AppShell.kt` — `shopContent` / `activityContent` スロット追加。
- `frontend/.../App.kt` — overlay 状態・`onOpenProduct` 配線・refresh DI・各タブ VM 生成。
- `frontend/src/commonMain/composeResources/values/strings.xml` — 文言追加。

**テスト（commonTest）**
- `core/ui/InventoryRefreshControllerTest.kt`
- `feature/shopping/ShoppingListViewModelTest.kt`
- `feature/activity/ActivityGroupingTest.kt`
- `feature/activity/ActivityViewModelTest.kt`
- `feature/inventory/ProductDetailViewModelTest.kt`（既存を新シグネチャへ書き換え）
- `feature/inventory/data/InventoryRepositoryTest.kt`（追加メソッドの委譲を追補）

---

## 共通ルール（全タスク）

- commonTest は **Kotest FunSpec 不可**。`kotlin.test.@Test` + Kotest assertions（`io.kotest.matchers.*`）+ `kotlinx.coroutines.test.runTest`/`runCurrent`。
- VM は `androidx.lifecycle.ViewModel` 継承。`load()` 等は `suspend`（Compose の `LaunchedEffect` から呼ぶ）。refresh の collect は **VM ではなく Compose 層**で行う（VM は `request()` のみ）。
- 文言はすべて string resource。非 Composable 層は `UiText`。
- 検証は `./gradlew :frontend:compileKotlinWasmJs` と `:frontend:jsTest`（`--tests` フィルタ非対応＝全体実行）。フルビルドは OOM 回避のため避ける。
- コミットは frequent。コミットメッセージに issue/PR 番号を書かない。末尾に `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`。

---

## Task 1: i18n 文言追加

**Files:**
- Modify: `frontend/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: 文言を追加（既存 placeholder は据置でよい）**

`</resources>` の直前に以下を追加する:

```xml
    <!-- P6-1b: 買い物リスト -->
    <string name="shop_subtitle">在庫が少ない商品と、自分で追加した商品</string>
    <string name="shop_title">買い物リスト</string>
    <string name="shop_add_from_stock_title">在庫から探して追加</string>
    <string name="shop_add_from_stock_sub">在庫はあるけど買い足したい商品を選ぶ</string>
    <string name="shop_progress">あと %1$d 点で買い物完了</string>
    <string name="shop_progress_count">%1$d/%2$d</string>
    <string name="shop_section_auto">在庫が少ない</string>
    <string name="shop_section_manual">自分で追加</string>
    <string name="shop_manual_badge">自分で追加</string>
    <string name="shop_stock_qty">在庫 %1$d%2$s</string>
    <string name="shop_shortage">目安 %1$d%2$s</string>
    <string name="shop_remove">リストから外す</string>
    <string name="shop_empty_title">買うものはありません</string>
    <string name="shop_empty_sub">在庫はぜんぶ足りています。買い足したい物は上から追加できます。</string>
    <string name="shop_add_search_placeholder">在庫の商品名で検索</string>
    <string name="shop_add_sheet_title">在庫から追加</string>
    <string name="shop_add_empty_all">すべての在庫がすでに買い物リストにあります。</string>
    <string name="shop_add_empty_none">該当する在庫がありません。</string>
    <string name="shop_add_action">追加</string>
    <string name="toast_added_to_list">買い物リストに追加しました</string>
    <string name="toast_removed_from_list">買い物リストから外しました</string>
    <!-- P6-1b: 活動 -->
    <string name="activity_subtitle">世帯のすべての記録</string>
    <string name="activity_title">履歴</string>
    <string name="activity_empty_title">まだ記録がありません</string>
    <string name="activity_empty_sub">補充や消費をすると、ここに事実が積み重なります。</string>
    <string name="activity_day_today">今日</string>
    <string name="activity_day_yesterday">昨日</string>
    <string name="activity_day_n_days_ago">%1$d日前</string>
    <string name="activity_row_summary">%1$s %2$d%3$s · %4$s</string>
    <!-- P6-1b: 商品詳細 wanted -->
    <string name="detail_wanted_auto">在庫が少ないため、買い物リストに表示中です</string>
    <string name="detail_wanted_add">買い物リストに入れる</string>
    <string name="detail_wanted_remove">買い物リストから外す</string>
```

注: 日付フォーマット（`%1$d日前`より古い＝具体日付）の文言は `ActivityGrouping` が `LocalDate.toString()`（ISO）を `UiText` ではなく素の文字列で返す（Task 6 参照）。曜日付き和文整形は P6-1b では行わない（YAGNI、ISO 表示）。

- [ ] **Step 2: コンパイル確認（リソース生成）**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL（生成リソース `Res.string.shop_title` 等が利用可能になる）

- [ ] **Step 3: Commit**

```bash
git add frontend/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(frontend): P6-1b の文言を strings.xml に追加"
```

---

## Task 2: InventoryRefreshController

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/ui/InventoryRefreshController.kt`
- Test: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/core/ui/InventoryRefreshControllerTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.frontend.core.ui

import io.kotest.matchers.collections.shouldHaveSize
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class InventoryRefreshControllerTest {
    @Test
    fun request_emits_signal() =
        runTest {
            val controller = InventoryRefreshController()
            val received = mutableListOf<Unit>()
            val job = launch { controller.signal.collect { received.add(it) } }
            runCurrent()
            controller.request()
            runCurrent()
            received shouldHaveSize 1
            job.cancel()
        }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :frontend:jsTest`
Expected: FAIL（`InventoryRefreshController` 未定義でコンパイルエラー）

- [ ] **Step 3: 最小実装**

```kotlin
package net.brightroom.mindstock.frontend.core.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 在庫に影響する mutation（補充/消費/訂正/setWanted）の単一シグナル。
 * mutation した VM が request()、各タブの一覧 VM を Compose 層で collect→reload する。
 */
class InventoryRefreshController {
    private val _signal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val signal: SharedFlow<Unit> = _signal.asSharedFlow()

    fun request() {
        _signal.tryEmit(Unit)
    }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :frontend:jsTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/ui/InventoryRefreshController.kt frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/core/ui/InventoryRefreshControllerTest.kt
git commit -m "feat(frontend): 在庫変更の共有 refresh シグナルを TDD で追加"
```

---

## Task 3: InventoryRepository に shoppingList / activity / setWanted 追加

**Files:**
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/data/InventoryRepository.kt`
- Test: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/inventory/data/InventoryRepositoryTest.kt`

- [ ] **Step 1: 失敗するテストを追補**

既存 `InventoryRepositoryTest.kt` に以下を追加（ファイル冒頭の `FakeProductRpc` に `setWanted` 等が増える点に注意。`ProductRegisterRpcService` の fake と `StockRpcService.activity` の fake を足す）。テストはまず `shoppingList` の委譲のみ追加:

```kotlin
    @Test
    fun shopping_list_returns_success_outcome_on_ok() =
        runTest {
            val fakeProduct =
                object : FakeProductRpc() {
                    override suspend fun shoppingList(householdId: HouseholdId) =
                        RpcResult.Ok(ShoppingList(emptyList()))
                }
            val repo =
                InventoryRepository(
                    productService = { fakeProduct },
                    stockService = { error("unused") },
                    stockRegisterService = { error("unused") },
                    productRegisterService = { error("unused") },
                )
            val out = repo.shoppingList(HouseholdId.create())
            out.shouldBeInstanceOf<RpcOutcome.Success<ShoppingList>>()
        }
```

既存の他テストも `InventoryRepository(...)` 呼び出しに `productRegisterService = { error("unused") }` を足す（コンストラクタ変更のため）。

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :frontend:jsTest`
Expected: FAIL（`productRegisterService` 引数未定義 / `shoppingList` メソッド未定義）

- [ ] **Step 3: Repository を拡張**

`InventoryRepository.kt` を以下に置き換え（import 追加: `ActivityFeed`, `ShoppingList`, `ProductRegisterRpcService`）:

```kotlin
package net.brightroom.mindstock.frontend.feature.inventory.data

import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingList
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Reason
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.toOutcome
import net.brightroom.mindstock.rpc.product.ProductRegisterRpcService
import net.brightroom.mindstock.rpc.product.ProductRpcService
import net.brightroom.mindstock.rpc.stock.ActivityFeed
import net.brightroom.mindstock.rpc.stock.StockRegisterRpcService
import net.brightroom.mindstock.rpc.stock.StockRpcService

/**
 * 在庫まわりの RPC を隠蔽。サービスは「開く関数」を遅延注入（認証後にトークン付きで open される）。
 */
class InventoryRepository(
    private val productService: () -> ProductRpcService,
    private val stockService: () -> StockRpcService,
    private val stockRegisterService: () -> StockRegisterRpcService,
    private val productRegisterService: () -> ProductRegisterRpcService,
) {
    suspend fun list(householdId: HouseholdId): RpcOutcome<Stocks> = productService().list(householdId).toOutcome()

    suspend fun shoppingList(householdId: HouseholdId): RpcOutcome<ShoppingList> =
        productService().shoppingList(householdId).toOutcome()

    suspend fun activity(householdId: HouseholdId): RpcOutcome<ActivityFeed> = stockService().activity(householdId).toOutcome()

    suspend fun history(productId: ProductId): RpcOutcome<StockMovements> = stockService().history(productId).toOutcome()

    suspend fun replenish(
        productId: ProductId,
        quantity: Quantity,
        note: Note,
    ): RpcOutcome<Unit> = stockRegisterService().replenish(productId, quantity, note).toOutcome()

    suspend fun consume(
        productId: ProductId,
        quantity: Quantity,
        note: Note,
    ): RpcOutcome<Unit> = stockRegisterService().consume(productId, quantity, note).toOutcome()

    suspend fun correct(
        target: MovementId,
        correctedQuantity: Quantity,
        reason: Reason,
    ): RpcOutcome<Unit> = stockRegisterService().correct(target, correctedQuantity, reason).toOutcome()

    suspend fun setWanted(
        productId: ProductId,
        wanted: Boolean,
    ): RpcOutcome<Unit> = productRegisterService().setWanted(productId, wanted).toOutcome()
}
```

注: `ProductRegisterRpcService` の正確なパッケージは `rpc/src/.../rpc/product/ProductRegisterRpcService.kt` より `net.brightroom.mindstock.rpc.product.ProductRegisterRpcService`。`StockRpcService.activity` / `ActivityFeed` のパッケージは `net.brightroom.mindstock.rpc.stock.*`。

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :frontend:jsTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/data/InventoryRepository.kt frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/inventory/data/InventoryRepositoryTest.kt
git commit -m "feat(frontend): InventoryRepository に shoppingList/activity/setWanted を追加"
```

---

## Task 4: InventoryViewModel に refresh 発火を追加

**Files:**
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/InventoryViewModel.kt`
- Test: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/inventory/InventoryViewModelTest.kt`

- [ ] **Step 1: 失敗するテストを追補**

既存 `vm(...)` ヘルパに `refresh` を足し、補充成功で refresh が発火するテストを追加:

```kotlin
    @Test
    fun replenish_success_emits_refresh() =
        runTest {
            var refreshed = 0
            val refresh = InventoryRefreshController()
            val job = launch { refresh.signal.collect { refreshed++ } }
            runCurrent()
            val v = vm(refresh = refresh)
            v.load()
            v.replenish(ProductId.create(), Quantity(1), Note(""))
            runCurrent()
            refreshed shouldBe 1
            job.cancel()
        }
```

`vm(...)` ヘルパに引数追加:

```kotlin
    refresh: InventoryRefreshController = InventoryRefreshController(),
```

と、`InventoryViewModel(... , refresh = refresh, toast = toast, reauth = reauth)`。import: `net.brightroom.mindstock.frontend.core.ui.InventoryRefreshController`。

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :frontend:jsTest`
Expected: FAIL（`refresh` 引数未定義）

- [ ] **Step 3: InventoryViewModel を変更**

コンストラクタに `private val refresh: InventoryRefreshController,` を `toast` の前に追加（import 追加）。`write(...)` の成功分岐で `load()` の後に `refresh.request()` を追加:

```kotlin
    private suspend fun write(
        outcome: RpcOutcome<Unit>,
        successText: UiText,
    ) {
        when (outcome) {
            is RpcOutcome.Success -> {
                load() // append-only のサーバ真実を再取得
                refresh.request() // 他タブ（買い物/活動）へ波及
                toast.show(successText)
            }

            is RpcOutcome.Failure -> {
                handleFailure(outcome.error)
            }
        }
    }
```

import 追加: `import net.brightroom.mindstock.frontend.core.ui.InventoryRefreshController`。

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :frontend:jsTest`
Expected: PASS（既存テストも通る）

- [ ] **Step 5: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/InventoryViewModel.kt frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/inventory/InventoryViewModelTest.kt
git commit -m "feat(frontend): InventoryViewModel の mutation 成功で refresh を発火"
```

---

## Task 5: ShoppingListUiState + ShoppingListViewModel

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/shopping/ShoppingListUiState.kt`
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/shopping/ShoppingListViewModel.kt`
- Test: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/shopping/ShoppingListViewModelTest.kt`

- [ ] **Step 1: UiState を作成**

```kotlin
package net.brightroom.mindstock.frontend.feature.shopping

import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingList
import net.brightroom.mindstock.frontend.core.ui.UiText

sealed interface ShoppingListUiState {
    data object Loading : ShoppingListUiState

    data class Content(
        val shoppingList: ShoppingList,
    ) : ShoppingListUiState {
        /** 在庫不足の自動アイテム。 */
        fun auto(): ShoppingList = shoppingList.autoItems()

        /** 手動希望のアイテム。 */
        fun manual(): ShoppingList = shoppingList.manualItems()

        /** 「在庫から探して追加」候補（まだリストに載っていない採用済み）。 */
        fun addable(): ShoppingList =
            ShoppingList(shoppingList.list.filter { !it.onList() })
    }

    data class Error(
        val text: UiText,
    ) : ShoppingListUiState
}
```

- [ ] **Step 2: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.frontend.feature.shopping

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingList
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.ui.InventoryRefreshController
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.rpc.result.RpcError
import kotlin.test.Test

private fun vm(
    loadShoppingList: suspend (HouseholdId) -> RpcOutcome<ShoppingList> = { RpcOutcome.Success(ShoppingList(emptyList())) },
    setWanted: suspend (ProductId, Boolean) -> RpcOutcome<Unit> = { _, _ -> RpcOutcome.Success(Unit) },
    replenish: suspend (ProductId, Quantity, Note) -> RpcOutcome<Unit> = { _, _, _ -> RpcOutcome.Success(Unit) },
    refresh: InventoryRefreshController = InventoryRefreshController(),
    toast: ToastController = ToastController(),
    reauth: ReauthController = ReauthController(),
) = ShoppingListViewModel(
    householdId = HouseholdId.create(),
    loadShoppingList = loadShoppingList,
    setWantedFlag = setWanted,
    replenishStock = replenish,
    refresh = refresh,
    toast = toast,
    reauth = reauth,
)

class ShoppingListViewModelTest {
    @Test
    fun load_success_sets_content() =
        runTest {
            val v = vm()
            v.load()
            v.state.value.shouldBeInstanceOf<ShoppingListUiState.Content>()
        }

    @Test
    fun load_failure_sets_error() =
        runTest {
            val v = vm(loadShoppingList = { RpcOutcome.Failure(RpcError.Internal("boom")) })
            v.load()
            v.state.value.shouldBeInstanceOf<ShoppingListUiState.Error>()
        }

    @Test
    fun set_wanted_success_reloads_and_emits_refresh() =
        runTest {
            var loads = 0
            var refreshed = 0
            val refresh = InventoryRefreshController()
            val job = launch { refresh.signal.collect { refreshed++ } }
            runCurrent()
            val v =
                vm(loadShoppingList = {
                    loads++
                    RpcOutcome.Success(ShoppingList(emptyList()))
                }, refresh = refresh)
            v.load()
            v.setWanted(ProductId.create(), false)
            runCurrent()
            loads shouldBe 2
            refreshed shouldBe 1
            job.cancel()
        }

    @Test
    fun unauthorized_requests_reauth() =
        runTest {
            var reauthRequested = 0
            val reauth = ReauthController()
            val job = launch { reauth.signal.collect { reauthRequested++ } }
            runCurrent()
            val v = vm(setWanted = { _, _ -> RpcOutcome.Failure(RpcError.Unauthorized("expired")) }, reauth = reauth)
            v.setWanted(ProductId.create(), true)
            runCurrent()
            reauthRequested shouldBe 1
            job.cancel()
        }
}
```

- [ ] **Step 3: テストが失敗することを確認**

Run: `./gradlew :frontend:jsTest`
Expected: FAIL（`ShoppingListViewModel` 未定義）

- [ ] **Step 4: ViewModel を実装**

```kotlin
package net.brightroom.mindstock.frontend.feature.shopping

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.toast_added_to_list
import mindstock.frontend.generated.resources.toast_removed_from_list
import mindstock.frontend.generated.resources.toast_replenished
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingList
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.errorText
import net.brightroom.mindstock.frontend.core.rpc.requiresReauth
import net.brightroom.mindstock.frontend.core.ui.InventoryRefreshController
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.frontend.core.ui.UiText
import net.brightroom.mindstock.rpc.result.RpcError

class ShoppingListViewModel(
    private val householdId: HouseholdId,
    private val loadShoppingList: suspend (HouseholdId) -> RpcOutcome<ShoppingList>,
    private val setWantedFlag: suspend (ProductId, Boolean) -> RpcOutcome<Unit>,
    private val replenishStock: suspend (ProductId, Quantity, Note) -> RpcOutcome<Unit>,
    private val refresh: InventoryRefreshController,
    private val toast: ToastController,
    private val reauth: ReauthController,
) : ViewModel() {
    private val _state = MutableStateFlow<ShoppingListUiState>(ShoppingListUiState.Loading)
    val state: StateFlow<ShoppingListUiState> = _state.asStateFlow()

    suspend fun load() {
        _state.value = ShoppingListUiState.Loading
        _state.value =
            when (val out = loadShoppingList(householdId)) {
                is RpcOutcome.Success -> ShoppingListUiState.Content(out.value)
                is RpcOutcome.Failure -> {
                    handleFailure(out.error)
                    ShoppingListUiState.Error(errorText(out.error))
                }
            }
    }

    suspend fun setWanted(
        productId: ProductId,
        wanted: Boolean,
    ) {
        val text = if (wanted) UiText(Res.string.toast_added_to_list) else UiText(Res.string.toast_removed_from_list)
        write(setWantedFlag(productId, wanted), text)
    }

    suspend fun replenish(
        productId: ProductId,
        quantity: Quantity,
        note: Note,
    ) = write(replenishStock(productId, quantity, note), UiText(Res.string.toast_replenished))

    private suspend fun write(
        outcome: RpcOutcome<Unit>,
        successText: UiText,
    ) {
        when (outcome) {
            is RpcOutcome.Success -> {
                load()
                refresh.request()
                toast.show(successText)
            }

            is RpcOutcome.Failure -> handleFailure(outcome.error)
        }
    }

    private fun handleFailure(error: RpcError) {
        if (error.requiresReauth()) reauth.request() else toast.show(errorText(error))
    }
}
```

- [ ] **Step 5: テストが通ることを確認**

Run: `./gradlew :frontend:jsTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/shopping/ frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/shopping/
git commit -m "feat(frontend): ShoppingListViewModel/UiState を TDD で追加"
```

---

## Task 6: ActivityGrouping（純関数）

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/activity/ActivityGrouping.kt`
- Test: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/activity/ActivityGroupingTest.kt`

設計: `ActivityFeed` を「日ラベル → エントリ群（occurredAt 降順）」のグループ列にする。日ラベルは `UiText`（今日/昨日/N日前）か ISO 日付文字列。`today: LocalDate` を注入して純関数化。

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.frontend.feature.activity

import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.activity_day_n_days_ago
import mindstock.frontend.generated.resources.activity_day_today
import mindstock.frontend.generated.resources.activity_day_yesterday
import kotlin.test.Test

class ActivityGroupingTest {
    private val today = LocalDate(2026, 6, 7)

    @Test
    fun today_label() {
        val d = LocalDateTime(2026, 6, 7, 9, 30)
        dayLabel(d, today) shouldBe DayLabel.Resource(Res.string.activity_day_today)
    }

    @Test
    fun yesterday_label() {
        val d = LocalDateTime(2026, 6, 6, 23, 0)
        dayLabel(d, today) shouldBe DayLabel.Resource(Res.string.activity_day_yesterday)
    }

    @Test
    fun n_days_ago_label() {
        val d = LocalDateTime(2026, 6, 4, 12, 0)
        dayLabel(d, today) shouldBe DayLabel.NDaysAgo(Res.string.activity_day_n_days_ago, 3)
    }

    @Test
    fun old_date_falls_back_to_iso() {
        val d = LocalDateTime(2026, 5, 1, 12, 0)
        dayLabel(d, today) shouldBe DayLabel.Date("2026-05-01")
    }

    @Test
    fun hm_pads_minutes() {
        hm(LocalDateTime(2026, 6, 7, 9, 5)) shouldBe "9:05"
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :frontend:jsTest`
Expected: FAIL（`dayLabel`/`DayLabel`/`hm` 未定義）

- [ ] **Step 3: 純関数を実装**

```kotlin
package net.brightroom.mindstock.frontend.feature.activity

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import net.brightroom.mindstock.rpc.stock.ActivityEntry
import net.brightroom.mindstock.rpc.stock.ActivityFeed
import org.jetbrains.compose.resources.StringResource

/** 活動行の日ラベル。UI 層で文言解決する。 */
sealed interface DayLabel {
    data class Resource(val resource: StringResource) : DayLabel

    data class NDaysAgo(val resource: StringResource, val days: Int) : DayLabel

    data class Date(val iso: String) : DayLabel
}

/** 同一日ラベルでまとめたグループ。entries は occurredAt 降順。 */
data class ActivityGroup(
    val label: DayLabel,
    val entries: List<ActivityEntry>,
)

private fun StringResource.label(): DayLabel = DayLabel.Resource(this)

fun dayLabel(
    occurredAt: LocalDateTime,
    today: LocalDate,
): DayLabel {
    val date = occurredAt.date
    val diff = today.toEpochDays() - date.toEpochDays()
    return when {
        diff <= 0 -> DayLabel.Resource(activityTodayResource())
        diff == 1 -> DayLabel.Resource(activityYesterdayResource())
        diff < 7 -> DayLabel.NDaysAgo(activityNDaysAgoResource(), diff.toInt())
        else -> DayLabel.Date(date.toString())
    }
}

fun hm(occurredAt: LocalDateTime): String =
    "${occurredAt.hour}:${occurredAt.minute.toString().padStart(2, '0')}"

/** ActivityFeed を occurredAt 降順に並べ、日ラベルでグループ化する。 */
fun ActivityFeed.groupedByDay(today: LocalDate): List<ActivityGroup> {
    val sorted = list.sortedByDescending { it.movement.occurredAt() }
    val result = mutableListOf<ActivityGroup>()
    for (entry in sorted) {
        val label = dayLabel(entry.movement.occurredAt(), today)
        val last = result.lastOrNull()
        if (last != null && last.label == label) {
            result[result.lastIndex] = last.copy(entries = last.entries + entry)
        } else {
            result.add(ActivityGroup(label, listOf(entry)))
        }
    }
    return result
}
```

注: `dayLabel` の `activityTodayResource()` 等は循環参照を避けるため、`Res.string.*` を直接使う。下のように import して使う（上の private helper は省き、直接参照に置き換える）:

```kotlin
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.activity_day_n_days_ago
import mindstock.frontend.generated.resources.activity_day_today
import mindstock.frontend.generated.resources.activity_day_yesterday
```

そして `dayLabel` 内は `DayLabel.Resource(Res.string.activity_day_today)` / `DayLabel.Resource(Res.string.activity_day_yesterday)` / `DayLabel.NDaysAgo(Res.string.activity_day_n_days_ago, diff.toInt())` を直接返す（`activityTodayResource()` 等のラッパは作らない）。`occurredAt()` は `OccurredAt.invoke()`（`StockMovement.occurredAt` は `OccurredAt` 値クラス、`movement.occurredAt()` で `LocalDateTime`）。

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :frontend:jsTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/activity/ActivityGrouping.kt frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/activity/ActivityGroupingTest.kt
git commit -m "feat(frontend): 活動の日付グルーピング純関数を TDD で追加"
```

---

## Task 7: ActivityUiState + ActivityViewModel

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/activity/ActivityUiState.kt`
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/activity/ActivityViewModel.kt`
- Test: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/activity/ActivityViewModelTest.kt`

- [ ] **Step 1: UiState を作成**

```kotlin
package net.brightroom.mindstock.frontend.feature.activity

import net.brightroom.mindstock.frontend.core.ui.UiText
import net.brightroom.mindstock.rpc.stock.ActivityFeed

sealed interface ActivityUiState {
    data object Loading : ActivityUiState

    data class Content(
        val feed: ActivityFeed,
    ) : ActivityUiState

    data class Error(
        val text: UiText,
    ) : ActivityUiState
}
```

- [ ] **Step 2: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.frontend.feature.activity

import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.stock.ActivityFeed
import kotlin.test.Test

private fun vm(
    loadActivity: suspend (HouseholdId) -> RpcOutcome<ActivityFeed> = { RpcOutcome.Success(ActivityFeed(emptyList())) },
    toast: ToastController = ToastController(),
    reauth: ReauthController = ReauthController(),
) = ActivityViewModel(
    householdId = HouseholdId.create(),
    loadActivity = loadActivity,
    toast = toast,
    reauth = reauth,
)

class ActivityViewModelTest {
    @Test
    fun load_success_sets_content() =
        runTest {
            val v = vm()
            v.load()
            v.state.value.shouldBeInstanceOf<ActivityUiState.Content>()
        }

    @Test
    fun load_failure_sets_error() =
        runTest {
            val v = vm(loadActivity = { RpcOutcome.Failure(RpcError.Internal("boom")) })
            v.load()
            v.state.value.shouldBeInstanceOf<ActivityUiState.Error>()
        }
}
```

- [ ] **Step 3: テストが失敗することを確認**

Run: `./gradlew :frontend:jsTest`
Expected: FAIL（`ActivityViewModel` 未定義）

- [ ] **Step 4: ViewModel を実装**

```kotlin
package net.brightroom.mindstock.frontend.feature.activity

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.errorText
import net.brightroom.mindstock.frontend.core.rpc.requiresReauth
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.stock.ActivityFeed

class ActivityViewModel(
    private val householdId: HouseholdId,
    private val loadActivity: suspend (HouseholdId) -> RpcOutcome<ActivityFeed>,
    private val toast: ToastController,
    private val reauth: ReauthController,
) : ViewModel() {
    private val _state = MutableStateFlow<ActivityUiState>(ActivityUiState.Loading)
    val state: StateFlow<ActivityUiState> = _state.asStateFlow()

    suspend fun load() {
        _state.value = ActivityUiState.Loading
        _state.value =
            when (val out = loadActivity(householdId)) {
                is RpcOutcome.Success -> ActivityUiState.Content(out.value)
                is RpcOutcome.Failure -> {
                    if (out.error.requiresReauth()) reauth.request() else toast.show(errorText(out.error))
                    ActivityUiState.Error(errorText(out.error))
                }
            }
    }
}
```

- [ ] **Step 5: テストが通ることを確認**

Run: `./gradlew :frontend:jsTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/activity/ActivityUiState.kt frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/activity/ActivityViewModel.kt frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/activity/ActivityViewModelTest.kt
git commit -m "feat(frontend): ActivityViewModel/UiState を TDD で追加"
```

---

## Task 8: ProductDetailViewModel を shoppingList 軸へ再設計

**Files:**
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ProductDetailUiState.kt`
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ProductDetailViewModel.kt`
- Test: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ProductDetailViewModelTest.kt`（全面書き換え）

- [ ] **Step 1: UiState を変更**

```kotlin
package net.brightroom.mindstock.frontend.feature.inventory

import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.frontend.core.ui.UiText

sealed interface ProductDetailUiState {
    data object Loading : ProductDetailUiState

    data class Content(
        val stock: Stock,
        val wanted: Boolean,
        val movements: StockMovements,
    ) : ProductDetailUiState

    data class Error(
        val text: UiText,
    ) : ProductDetailUiState
}
```

- [ ] **Step 2: 失敗するテストを書く（既存テストを置換）**

既存 `ProductDetailViewModelTest.kt` を以下で置換。テスト用 Stock 生成ヘルパを定義（`StockHomePreview.kt` の構成に倣う）:

```kotlin
package net.brightroom.mindstock.frontend.feature.inventory

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.ProductStatus
import net.brightroom.mindstock.domain.model.inventory.product.image.ProductImage
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.product.setting.StockingPolicy
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingEntry
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingList
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
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
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.ui.InventoryRefreshController
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.rpc.result.RpcError
import kotlin.test.Test

private val actor = Resident(ResidentId.create(), Profile(DisplayName("テスト")))

private fun stockOf(
    id: ProductId,
    net: Int,
    min: Int = 1,
): Stock {
    val product =
        Product(
            id = id,
            name = ProductName("牛乳"),
            barcode = Barcode.Unlinked,
            setting = StockingPolicy(ProductUnit("本"), MinimumStock(min)),
            image = ProductImage.None,
            status = ProductStatus.採用中,
        )
    val movements =
        StockMovements(
            listOf(
                StockMovement.Replenishment(
                    identity = MovementIdentity.Pending,
                    quantity = Quantity(net),
                    occurredAt = OccurredAt.now(),
                    actor = actor,
                    note = Note(""),
                ),
            ),
        )
    return Stock(product, movements)
}

private fun vm(
    productId: ProductId,
    seed: Stock? = null,
    loadShoppingList: suspend (HouseholdId) -> RpcOutcome<ShoppingList> = { RpcOutcome.Success(ShoppingList(emptyList())) },
    loadHistory: suspend (ProductId) -> RpcOutcome<StockMovements> = { RpcOutcome.Success(StockMovements(emptyList())) },
    replenish: suspend (ProductId, Quantity, Note) -> RpcOutcome<Unit> = { _, _, _ -> RpcOutcome.Success(Unit) },
    consume: suspend (ProductId, Quantity, Note) -> RpcOutcome<Unit> = { _, _, _ -> RpcOutcome.Success(Unit) },
    correct: suspend (net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId, Quantity, Reason) -> RpcOutcome<Unit> = { _, _, _ -> RpcOutcome.Success(Unit) },
    setWanted: suspend (ProductId, Boolean) -> RpcOutcome<Unit> = { _, _ -> RpcOutcome.Success(Unit) },
    refresh: InventoryRefreshController = InventoryRefreshController(),
    toast: ToastController = ToastController(),
    reauth: ReauthController = ReauthController(),
) = ProductDetailViewModel(
    householdId = HouseholdId.create(),
    productId = productId,
    seed = seed,
    loadShoppingList = loadShoppingList,
    loadHistory = loadHistory,
    replenishStock = replenish,
    consumeStock = consume,
    correctMovement = correct,
    setWantedFlag = setWanted,
    refresh = refresh,
    toast = toast,
    reauth = reauth,
)

class ProductDetailViewModelTest {
    @Test
    fun load_resolves_stock_and_wanted_from_shopping_list() =
        runTest {
            val pid = ProductId.create()
            val entry = ShoppingEntry(stock = stockOf(pid, net = 5, min = 1), manuallyWanted = true)
            val v = vm(productId = pid, loadShoppingList = { RpcOutcome.Success(ShoppingList(listOf(entry))) })
            v.load()
            val content = v.state.value.shouldBeInstanceOf<ProductDetailUiState.Content>()
            content.wanted shouldBe true
            content.stock.product.id shouldBe pid
        }

    @Test
    fun load_uses_seed_when_entry_absent() =
        runTest {
            val pid = ProductId.create()
            val seed = stockOf(pid, net = 2)
            val v = vm(productId = pid, seed = seed, loadShoppingList = { RpcOutcome.Success(ShoppingList(emptyList())) })
            v.load()
            val content = v.state.value.shouldBeInstanceOf<ProductDetailUiState.Content>()
            content.wanted shouldBe false
            content.stock.product.id shouldBe pid
        }

    @Test
    fun load_errors_when_no_entry_and_no_seed() =
        runTest {
            val pid = ProductId.create()
            val v = vm(productId = pid, loadShoppingList = { RpcOutcome.Success(ShoppingList(emptyList())) })
            v.load()
            v.state.value.shouldBeInstanceOf<ProductDetailUiState.Error>()
        }

    @Test
    fun set_wanted_success_reloads_and_emits_refresh() =
        runTest {
            val pid = ProductId.create()
            val entry = ShoppingEntry(stock = stockOf(pid, net = 5), manuallyWanted = false)
            var loads = 0
            var refreshed = 0
            val refresh = InventoryRefreshController()
            val job = launch { refresh.signal.collect { refreshed++ } }
            runCurrent()
            val v =
                vm(
                    productId = pid,
                    loadShoppingList = {
                        loads++
                        RpcOutcome.Success(ShoppingList(listOf(entry)))
                    },
                    refresh = refresh,
                )
            v.load()
            v.setWanted(pid, true)
            runCurrent()
            loads shouldBe 2
            refreshed shouldBe 1
            job.cancel()
        }

    @Test
    fun unauthorized_on_load_requests_reauth() =
        runTest {
            var reauthRequested = 0
            val reauth = ReauthController()
            val job = launch { reauth.signal.collect { reauthRequested++ } }
            runCurrent()
            val v = vm(productId = ProductId.create(), loadShoppingList = { RpcOutcome.Failure(RpcError.Unauthorized("expired")) }, reauth = reauth)
            v.load()
            runCurrent()
            reauthRequested shouldBe 1
            job.cancel()
        }
}
```

- [ ] **Step 3: テストが失敗することを確認**

Run: `./gradlew :frontend:jsTest`
Expected: FAIL（`ProductDetailViewModel` の新シグネチャ未定義）

- [ ] **Step 4: ViewModel を再実装**

```kotlin
package net.brightroom.mindstock.frontend.feature.inventory

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.toast_added_to_list
import mindstock.frontend.generated.resources.toast_consumed
import mindstock.frontend.generated.resources.toast_corrected
import mindstock.frontend.generated.resources.toast_removed_from_list
import mindstock.frontend.generated.resources.toast_replenished
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingList
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Reason
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.errorText
import net.brightroom.mindstock.frontend.core.rpc.requiresReauth
import net.brightroom.mindstock.frontend.core.ui.InventoryRefreshController
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.frontend.core.ui.UiText
import net.brightroom.mindstock.rpc.result.RpcError

class ProductDetailViewModel(
    private val householdId: HouseholdId,
    private val productId: ProductId,
    private val seed: Stock?,
    private val loadShoppingList: suspend (HouseholdId) -> RpcOutcome<ShoppingList>,
    private val loadHistory: suspend (ProductId) -> RpcOutcome<StockMovements>,
    private val replenishStock: suspend (ProductId, Quantity, Note) -> RpcOutcome<Unit>,
    private val consumeStock: suspend (ProductId, Quantity, Note) -> RpcOutcome<Unit>,
    private val correctMovement: suspend (MovementId, Quantity, Reason) -> RpcOutcome<Unit>,
    private val setWantedFlag: suspend (ProductId, Boolean) -> RpcOutcome<Unit>,
    private val refresh: InventoryRefreshController,
    private val toast: ToastController,
    private val reauth: ReauthController,
) : ViewModel() {
    private val _state = MutableStateFlow<ProductDetailUiState>(ProductDetailUiState.Loading)
    val state: StateFlow<ProductDetailUiState> = _state.asStateFlow()

    suspend fun load() {
        _state.value = ProductDetailUiState.Loading

        // Stock + wanted を shoppingList から解決
        val resolved: Pair<Stock, Boolean>? =
            when (val out = loadShoppingList(householdId)) {
                is RpcOutcome.Success -> {
                    val entry = out.value.list.firstOrNull { it.stock.product.id == productId }
                    when {
                        entry != null -> entry.stock to entry.manuallyWanted
                        seed != null -> seed to false
                        else -> null
                    }
                }

                is RpcOutcome.Failure -> {
                    handleFailure(out.error)
                    _state.value = ProductDetailUiState.Error(errorText(out.error))
                    return
                }
            }

        if (resolved == null) {
            _state.value = ProductDetailUiState.Error(errorText(RpcError.NotFound))
            return
        }

        val (stock, wanted) = resolved
        val movements =
            when (val out = loadHistory(productId)) {
                is RpcOutcome.Success -> out.value
                is RpcOutcome.Failure -> {
                    handleFailure(out.error)
                    _state.value = ProductDetailUiState.Error(errorText(out.error))
                    return
                }
            }
        _state.value = ProductDetailUiState.Content(stock, wanted, movements)
    }

    suspend fun replenish(
        quantity: Quantity,
        note: Note,
    ) = write(replenishStock(productId, quantity, note), UiText(Res.string.toast_replenished))

    suspend fun consume(
        quantity: Quantity,
        note: Note,
    ) = write(consumeStock(productId, quantity, note), UiText(Res.string.toast_consumed))

    suspend fun correct(
        target: MovementId,
        correctedQuantity: Quantity,
        reason: Reason,
    ) = write(correctMovement(target, correctedQuantity, reason), UiText(Res.string.toast_corrected))

    suspend fun setWanted(
        productId: ProductId,
        wanted: Boolean,
    ) {
        val text = if (wanted) UiText(Res.string.toast_added_to_list) else UiText(Res.string.toast_removed_from_list)
        write(setWantedFlag(productId, wanted), text)
    }

    private suspend fun write(
        outcome: RpcOutcome<Unit>,
        successText: UiText,
    ) {
        when (outcome) {
            is RpcOutcome.Success -> {
                load()
                refresh.request()
                toast.show(successText)
            }

            is RpcOutcome.Failure -> handleFailure(outcome.error)
        }
    }

    private fun handleFailure(error: RpcError) {
        if (error.requiresReauth()) reauth.request() else toast.show(errorText(error))
    }
}
```

- [ ] **Step 5: テストが通ることを確認**

Run: `./gradlew :frontend:jsTest`
Expected: PASS（この時点で `ProductDetailScreen`/`InventoryRoute` はまだ旧シグネチャを参照しコンパイルエラーの可能性 → 次タスクで解消。`jsTest` がコンパイル全体を要するため、Task 9・10・11 まで一気に進めてから `jsTest` する運用でもよい。ここでは「テストコードが新 VM に対し論理的に正しい」ことを確認し、Task 11 後にまとめてグリーン化する。）

- [ ] **Step 6: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ProductDetailUiState.kt frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ProductDetailViewModel.kt frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ProductDetailViewModelTest.kt
git commit -m "feat(frontend): ProductDetailViewModel を shoppingList 軸へ再設計(wanted/setWanted/mutation)"
```

---

## Task 9: ProductDetailScreen を新 Content 形 + wanted トグル対応へ

**Files:**
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/ProductDetailScreen.kt`

`ProductDetailScreen` を `stock` 引数を取らず、`state` 内 `Content.stock` と `seed`（ロード中ヘッダ用）から描画する形へ変更。wanted トグル領域を追加。`onReplenish`/`onConsume` は引数なし（VM 側 productId 固定）、`onCorrect`、`onToggleWanted(wanted: Boolean)` コールバックを受ける。

- [ ] **Step 1: ProductDetailScreen を書き換え**

```kotlin
package net.brightroom.mindstock.frontend.feature.inventory.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.action_consume
import mindstock.frontend.generated.resources.action_correct
import mindstock.frontend.generated.resources.action_replenish
import mindstock.frontend.generated.resources.correct_reason_placeholder
import mindstock.frontend.generated.resources.correct_submit
import mindstock.frontend.generated.resources.correct_title
import mindstock.frontend.generated.resources.detail_history
import mindstock.frontend.generated.resources.detail_history_empty
import mindstock.frontend.generated.resources.detail_min_stock
import mindstock.frontend.generated.resources.detail_wanted_add
import mindstock.frontend.generated.resources.detail_wanted_auto
import mindstock.frontend.generated.resources.detail_wanted_remove
import mindstock.frontend.generated.resources.history_consume
import mindstock.frontend.generated.resources.history_corrected_badge
import mindstock.frontend.generated.resources.history_replenish
import mindstock.frontend.generated.resources.loading
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.StockStatus
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementIdentity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement
import net.brightroom.mindstock.frontend.core.ui.resolve
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.PrimaryButton
import net.brightroom.mindstock.frontend.designsystem.atom.RoundBtn
import net.brightroom.mindstock.frontend.designsystem.atom.Sheet
import net.brightroom.mindstock.frontend.designsystem.atom.StatusDot
import net.brightroom.mindstock.frontend.designsystem.atom.Stepper
import net.brightroom.mindstock.frontend.designsystem.atom.StockLevelBar
import net.brightroom.mindstock.frontend.designsystem.atom.TextInput
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.feature.inventory.ProductDetailUiState
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProductDetailScreen(
    detail: ProductDetailUiState,
    seed: Stock?,
    onBack: () -> Unit,
    onReplenish: () -> Unit,
    onConsume: () -> Unit,
    onCorrect: (target: MovementId, quantity: Int, reason: String) -> Unit,
    onToggleWanted: (wanted: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // ヘッダの Stock: Content があればそれ、無ければ seed
    val stock: Stock? = (detail as? ProductDetailUiState.Content)?.stock ?: seed
    val wanted: Boolean? = (detail as? ProductDetailUiState.Content)?.wanted

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (stock == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundBtn(AppIconName.Back, contentDescription = "back", onClick = onBack)
            }
            if (detail is ProductDetailUiState.Error) AppText(detail.text.resolve()) else AppText(stringResource(Res.string.loading))
            return@Column
        }

        val tokens = LocalMindstockTokens.current
        val statusColor =
            when (stock.status()) {
                StockStatus.在庫切れ -> tokens.statusOut
                StockStatus.残りわずか -> tokens.statusLow
                StockStatus.十分 -> tokens.statusOk
            }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundBtn(AppIconName.Back, contentDescription = "back", onClick = onBack)
            AppText(stock.product.name())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(color = statusColor)
            AppText("${stock.currentQuantity()}${stock.product.setting.unit()}")
        }
        StockLevelBar(qty = stock.currentQuantity(), min = stock.product.setting.minimumStock(), color = statusColor)
        AppText(
            stringResource(
                Res.string.detail_min_stock,
                stock.product.setting.minimumStock(),
                stock.product.setting.unit(),
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(onClick = onReplenish) { AppText(stringResource(Res.string.action_replenish)) }
            PrimaryButton(onClick = onConsume) { AppText(stringResource(Res.string.action_consume)) }
        }

        // wanted トグル領域（status==十分 のときのみ操作可能。それ以外は自動表示の案内）
        when {
            stock.status() != StockStatus.十分 -> AppText(stringResource(Res.string.detail_wanted_auto))
            wanted == true -> PrimaryButton(onClick = { onToggleWanted(false) }) { AppText(stringResource(Res.string.detail_wanted_remove)) }
            wanted == false -> PrimaryButton(onClick = { onToggleWanted(true) }) { AppText(stringResource(Res.string.detail_wanted_add)) }
            else -> Unit // wanted 未確定（ロード中）はトグル非表示
        }

        AppText(stringResource(Res.string.detail_history))
        when (detail) {
            is ProductDetailUiState.Loading -> AppText(stringResource(Res.string.loading))
            is ProductDetailUiState.Error -> AppText(detail.text.resolve())
            is ProductDetailUiState.Content -> {
                val correctedIds =
                    detail.movements.list
                        .filterIsInstance<StockMovement.Correction>()
                        .map { it.target }
                        .toSet()
                if (detail.movements.list.isEmpty()) {
                    AppText(stringResource(Res.string.detail_history_empty))
                } else {
                    var correcting by remember { mutableStateOf<StockMovement?>(null) }
                    LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(detail.movements.list.reversed()) { m ->
                            val corrected = (m.identity as? MovementIdentity.Persisted)?.id in correctedIds
                            HistoryRow(m, stock.product.setting.unit(), corrected = corrected, onCorrect = { correcting = m })
                        }
                    }
                    CorrectionSheet(target = correcting, unit = stock.product.setting.unit(), onClose = { correcting = null }, onCorrect = onCorrect)
                }
            }
        }
    }
}

@Composable
private fun CorrectionSheet(
    target: StockMovement?,
    unit: String,
    onClose: () -> Unit,
    onCorrect: (target: MovementId, quantity: Int, reason: String) -> Unit,
) {
    if (target == null) return
    val movementId = (target.identity as? MovementIdentity.Persisted)?.id
    var qty by remember(target) { mutableStateOf(target.quantity()) }
    var reason by remember(target) { mutableStateOf("") }
    Sheet(open = true, title = stringResource(Res.string.correct_title), onClose = onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Stepper(value = qty, onChange = { qty = it }, unit = unit)
            TextInput(
                value = reason,
                onValueChange = { reason = it },
                placeholder = stringResource(Res.string.correct_reason_placeholder),
                modifier = Modifier.fillMaxWidth(),
                isError = reason.isBlank(),
            )
            PrimaryButton(
                onClick = {
                    if (movementId != null && reason.isNotBlank()) {
                        onCorrect(movementId, qty, reason)
                        onClose()
                    }
                },
                enabled = movementId != null && reason.isNotBlank(),
            ) { AppText(stringResource(Res.string.correct_submit)) }
        }
    }
}

@Composable
private fun HistoryRow(
    movement: StockMovement,
    unit: String,
    corrected: Boolean,
    onCorrect: () -> Unit,
) {
    val label =
        when (movement) {
            is StockMovement.Replenishment -> stringResource(Res.string.history_replenish)
            is StockMovement.Consumption -> stringResource(Res.string.history_consume)
            is StockMovement.Correction -> stringResource(Res.string.action_correct)
        }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        AppText("$label ${movement.quantity()}$unit")
        AppText(movement.actor.profile.displayName())
        if (movement.note().isNotEmpty()) AppText(movement.note())
        if (corrected) AppText(stringResource(Res.string.history_corrected_badge))
        if (movement is StockMovement.Replenishment || movement is StockMovement.Consumption) {
            PrimaryButton(onClick = onCorrect) { AppText(stringResource(Res.string.action_correct)) }
        }
    }
}
```

注: `action_consume` 文字列は既存にあるか確認（`action_replenish`/`action_consume` は P6-1a で使用済み＝存在）。`StockStatus` の variant 名（`在庫切れ`/`残りわずか`/`十分`）は既存 ProductDetailScreen に合わせる。

- [ ] **Step 2: 単体コンパイルは Task 10/11 とまとめて確認**

この時点では `ProductDetailOverlay`/`InventoryRoute` 未更新のためビルドは通らない。Task 11 後にまとめて `compileKotlinWasmJs`。

- [ ] **Step 3: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/ProductDetailScreen.kt
git commit -m "feat(frontend): ProductDetailScreen を新 Content 形 + wanted トグル対応へ"
```

---

## Task 10: ProductDetailOverlay（詳細 + MoveSheet 内包）

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/ProductDetailOverlay.kt`

`DetailTarget`（productId + 任意 seed Stock）と、それを受けて `ProductDetailViewModel` を駆動するオーバーレイ。詳細の補充/消費は内包 `MoveSheet`（既存 atom 流用、`MoveMode`）で。

- [ ] **Step 1: 作成**

```kotlin
package net.brightroom.mindstock.frontend.feature.inventory.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Reason
import net.brightroom.mindstock.frontend.core.ui.InventoryRefreshController
import net.brightroom.mindstock.frontend.feature.inventory.ProductDetailViewModel

/** app 層から開く商品詳細オーバーレイのターゲット。 */
data class DetailTarget(
    val productId: ProductId,
    val seed: Stock?,
)

@Composable
fun ProductDetailOverlay(
    target: DetailTarget,
    viewModelFactory: (DetailTarget) -> ProductDetailViewModel,
    refresh: InventoryRefreshController,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val vm = remember(target) { viewModelFactory(target) }
    val state by vm.state.collectAsState()
    var moveMode by remember(target) { mutableStateOf<MoveMode?>(null) }

    LaunchedEffect(target) { vm.load() }
    // 他タブ由来の変更（裏で発火）を詳細にも反映
    LaunchedEffect(target) { refresh.signal.collect { vm.load() } }

    ProductDetailScreen(
        detail = state,
        seed = target.seed,
        onBack = onBack,
        onReplenish = { moveMode = MoveMode.Replenish },
        onConsume = { moveMode = MoveMode.Consume },
        onCorrect = { mid: MovementId, qty: Int, reason: String ->
            scope.launch { vm.correct(mid, Quantity(qty), Reason(reason)) }
        },
        onToggleWanted = { wanted -> scope.launch { vm.setWanted(target.productId, wanted) } },
        modifier = modifier.fillMaxSize(),
    )

    val mode = moveMode
    // MoveSheet は対象 Stock が要る。Content の stock を使う（無ければ seed）。
    val stock: Stock? =
        (state as? net.brightroom.mindstock.frontend.feature.inventory.ProductDetailUiState.Content)?.stock ?: target.seed
    MoveSheet(
        open = mode != null && stock != null,
        mode = mode ?: MoveMode.Replenish,
        stock = stock,
        onClose = { moveMode = null },
        onSubmit = { quantity, note ->
            val m = mode ?: return@MoveSheet
            scope.launch {
                when (m) {
                    MoveMode.Replenish -> vm.replenish(Quantity(quantity), Note(note))
                    MoveMode.Consume -> vm.consume(Quantity(quantity), Note(note))
                }
            }
            moveMode = null
        },
    )
}
```

注: `MoveSheet`/`MoveMode` の正確なシグネチャは既存 `feature/inventory/ui/MoveSheet.kt` を参照（`MoveSheet(open, mode, stock, onClose, onSubmit: (quantity: Int, note: String) -> Unit)`）。`ProductDetailUiState.Content` の完全修飾は import に置換してよい。

- [ ] **Step 2: Commit（ビルドは Task 11 後）**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/ProductDetailOverlay.kt
git commit -m "feat(frontend): ProductDetailOverlay(詳細+MoveSheet)を app 層向けに追加"
```

---

## Task 11: 画面（Shop/Activity UI）+ InventoryRoute 簡素化 + AppShell/App 配線

**Files:**
- Create: `frontend/.../feature/shopping/ui/ShoppingListScreen.kt`
- Create: `frontend/.../feature/shopping/ui/AddToListSheet.kt`
- Create: `frontend/.../feature/activity/ui/ActivityScreen.kt`
- Modify: `frontend/.../feature/inventory/ui/InventoryRoute.kt`
- Modify: `frontend/.../app/shell/AppShell.kt`
- Modify: `frontend/.../App.kt`

- [ ] **Step 1: ShoppingListScreen を作成**

行は既存 `ProductCard`/`StockHomeScreen` のトーン・atom（`Thumb`/`StatusDot`/`PrimaryButton`/`Sheet`/`SearchField` 等）に倣う。`done` チェックはローカル一時状態。

```kotlin
package net.brightroom.mindstock.frontend.feature.shopping.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.shop_add_from_stock_sub
import mindstock.frontend.generated.resources.shop_add_from_stock_title
import mindstock.frontend.generated.resources.shop_empty_sub
import mindstock.frontend.generated.resources.shop_empty_title
import mindstock.frontend.generated.resources.shop_manual_badge
import mindstock.frontend.generated.resources.shop_progress
import mindstock.frontend.generated.resources.shop_progress_count
import mindstock.frontend.generated.resources.shop_remove
import mindstock.frontend.generated.resources.shop_section_auto
import mindstock.frontend.generated.resources.shop_section_manual
import mindstock.frontend.generated.resources.shop_stock_qty
import mindstock.frontend.generated.resources.shop_subtitle
import mindstock.frontend.generated.resources.shop_title
import mindstock.frontend.generated.resources.action_replenish
import mindstock.frontend.generated.resources.loading
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingEntry
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.frontend.core.ui.resolve
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.PrimaryButton
import net.brightroom.mindstock.frontend.feature.inventory.ui.MoveMode
import net.brightroom.mindstock.frontend.feature.inventory.ui.MoveSheet
import net.brightroom.mindstock.frontend.feature.shopping.ShoppingListUiState
import org.jetbrains.compose.resources.stringResource

@Composable
fun ShoppingListScreen(
    state: ShoppingListUiState,
    onOpenProduct: (ProductId, Stock?) -> Unit,
    onSetWanted: (ProductId, Boolean) -> Unit,
    onReplenish: (ProductId) -> Unit,
    modifier: Modifier = Modifier,
) {
    var addOpen by remember { mutableStateOf(false) }
    var moveTarget by remember { mutableStateOf<Stock?>(null) }
    val done = remember { mutableStateMapOf<String, Boolean>() }

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppText(stringResource(Res.string.shop_subtitle))
        AppText(stringResource(Res.string.shop_title))

        // 在庫から探して追加
        PrimaryButton(onClick = { addOpen = true }) { AppText(stringResource(Res.string.shop_add_from_stock_title)) }
        AppText(stringResource(Res.string.shop_add_from_stock_sub))

        when (state) {
            is ShoppingListUiState.Loading -> AppText(stringResource(Res.string.loading))
            is ShoppingListUiState.Error -> AppText(state.text.resolve())
            is ShoppingListUiState.Content -> {
                val auto = state.auto().list
                val manual = state.manual().list
                val items = auto + manual
                if (items.isEmpty()) {
                    AppText(stringResource(Res.string.shop_empty_title))
                    AppText(stringResource(Res.string.shop_empty_sub))
                } else {
                    val total = items.size
                    val remaining = items.count { done[it.stock.product.id.toString()] != true }
                    AppText(stringResource(Res.string.shop_progress, remaining))
                    AppText(stringResource(Res.string.shop_progress_count, total - remaining, total))
                    LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (auto.isNotEmpty()) {
                            item { AppText(stringResource(Res.string.shop_section_auto)) }
                            items(auto) { entry -> ShopRow(entry, isManual = false, done, onOpenProduct, onSetWanted, onReplenish = { moveTarget = entry.stock }) }
                        }
                        if (manual.isNotEmpty()) {
                            item { AppText(stringResource(Res.string.shop_section_manual)) }
                            items(manual) { entry -> ShopRow(entry, isManual = true, done, onOpenProduct, onSetWanted, onReplenish = { moveTarget = entry.stock }) }
                        }
                    }
                }
            }
        }
    }

    AddToListSheet(
        open = addOpen,
        candidates = (state as? ShoppingListUiState.Content)?.addable()?.list ?: emptyList(),
        onClose = { addOpen = false },
        onAdd = { pid -> onSetWanted(pid, true) },
    )

    val mt = moveTarget
    MoveSheet(
        open = mt != null,
        mode = MoveMode.Replenish,
        stock = mt,
        onClose = { moveTarget = null },
        onSubmit = { quantity, note ->
            val s = mt ?: return@MoveSheet
            onReplenish(s.product.id) // 数量つき補充は ViewModel 経由（下記注参照）
            moveTarget = null
        },
    )
}

@Composable
private fun ShopRow(
    entry: ShoppingEntry,
    isManual: Boolean,
    done: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Boolean>,
    onOpenProduct: (ProductId, Stock?) -> Unit,
    onSetWanted: (ProductId, Boolean) -> Unit,
    onReplenish: () -> Unit,
) {
    val pid = entry.stock.product.id
    val key = pid.toString()
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        PrimaryButton(onClick = { done[key] = !(done[key] ?: false) }) { AppText(if (done[key] == true) "✓" else "○") }
        PrimaryButton(onClick = { onOpenProduct(pid, entry.stock) }) { AppText(entry.stock.product.name()) }
        if (isManual) {
            AppText(stringResource(Res.string.shop_manual_badge))
            PrimaryButton(onClick = { onSetWanted(pid, false) }) { AppText(stringResource(Res.string.shop_remove)) }
        }
        PrimaryButton(onClick = onReplenish) { AppText(stringResource(Res.string.action_replenish)) }
    }
}
```

注（MoveSheet 数量問題）: `onReplenish: (ProductId) -> Unit` だけでは数量が渡らない。`ShoppingListScreen` の `MoveSheet.onSubmit` で `quantity`/`note` を ViewModel に渡すため、`onReplenish` を `(ProductId, Int, String) -> Unit` に変更し、route 側で `vm.replenish(pid, Quantity(q), Note(n))` を呼ぶこと。上のシグネチャを `onReplenish: (ProductId, Int, String) -> Unit` に直し、行の `補充` ボタンは `moveTarget = entry.stock` を開くだけ、`MoveSheet.onSubmit` で `onReplenish(s.product.id, quantity, note)` を呼ぶ。`ShopRow` の `onReplenish: () -> Unit` は「シートを開く」用途のまま。

実装時はこの数量フローに統一すること（行 → シート → onSubmit(qty,note) → VM.replenish）。スタイルは `ProductCard`/`StockHomeScreen` のトークン・atom に寄せて clay 忠実化（`frontend-visual-fidelity-expectation`）。チェック丸・進捗バナー・破線追加ボタンは既存 atom + `LocalMindstockTokens` で構成。

- [ ] **Step 2: AddToListSheet を作成**

```kotlin
package net.brightroom.mindstock.frontend.feature.shopping.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.shop_add_action
import mindstock.frontend.generated.resources.shop_add_empty_all
import mindstock.frontend.generated.resources.shop_add_search_placeholder
import mindstock.frontend.generated.resources.shop_add_sheet_title
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingEntry
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.PrimaryButton
import net.brightroom.mindstock.frontend.designsystem.atom.Sheet
import net.brightroom.mindstock.frontend.designsystem.atom.TextInput
import org.jetbrains.compose.resources.stringResource

@Composable
fun AddToListSheet(
    open: Boolean,
    candidates: List<ShoppingEntry>,
    onClose: () -> Unit,
    onAdd: (ProductId) -> Unit,
) {
    if (!open) return
    var query by remember { mutableStateOf("") }
    val results = candidates.filter { query.isBlank() || it.stock.product.name().contains(query, ignoreCase = true) }
    Sheet(open = true, title = stringResource(Res.string.shop_add_sheet_title), onClose = onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextInput(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(Res.string.shop_add_search_placeholder),
                modifier = Modifier.fillMaxWidth(),
            )
            if (results.isEmpty()) {
                AppText(stringResource(Res.string.shop_add_empty_all))
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results) { entry ->
                        PrimaryButton(onClick = { onAdd(entry.stock.product.id) }) {
                            AppText(entry.stock.product.name() + " · " + stringResource(Res.string.shop_add_action))
                        }
                    }
                }
            }
        }
    }
}
```

注: `Arrangement.spacedBy(12.dp)` の `dp` import 漏れに注意（`androidx.compose.ui.unit.dp`）。

- [ ] **Step 3: ActivityScreen を作成**

```kotlin
package net.brightroom.mindstock.frontend.feature.activity.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.activity_day_n_days_ago
import mindstock.frontend.generated.resources.activity_day_today
import mindstock.frontend.generated.resources.activity_day_yesterday
import mindstock.frontend.generated.resources.activity_empty_sub
import mindstock.frontend.generated.resources.activity_empty_title
import mindstock.frontend.generated.resources.activity_row_summary
import mindstock.frontend.generated.resources.activity_subtitle
import mindstock.frontend.generated.resources.activity_title
import mindstock.frontend.generated.resources.history_consume
import mindstock.frontend.generated.resources.history_replenish
import mindstock.frontend.generated.resources.loading
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement
import net.brightroom.mindstock.extensions.kotlinx.datetime.now
import net.brightroom.mindstock.frontend.core.ui.resolve
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.PrimaryButton
import net.brightroom.mindstock.frontend.feature.activity.ActivityUiState
import net.brightroom.mindstock.frontend.feature.activity.DayLabel
import net.brightroom.mindstock.frontend.feature.activity.groupedByDay
import net.brightroom.mindstock.frontend.feature.activity.hm
import org.jetbrains.compose.resources.stringResource

@Composable
fun ActivityScreen(
    state: ActivityUiState,
    onOpenProduct: (ProductId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppText(stringResource(Res.string.activity_subtitle))
        AppText(stringResource(Res.string.activity_title))
        when (state) {
            is ActivityUiState.Loading -> AppText(stringResource(Res.string.loading))
            is ActivityUiState.Error -> AppText(state.text.resolve())
            is ActivityUiState.Content -> {
                val today = LocalDate.now(TimeZone.JST)
                val groups = state.feed.groupedByDay(today)
                if (groups.isEmpty()) {
                    AppText(stringResource(Res.string.activity_empty_title))
                    AppText(stringResource(Res.string.activity_empty_sub))
                } else {
                    // 訂正済 target 集合
                    val correctedIds =
                        state.feed.list
                            .map { it.movement }
                            .filterIsInstance<StockMovement.Correction>()
                            .map { it.target }
                            .toSet()
                    LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        groups.forEach { group ->
                            item { AppText(dayLabelText(group.label)) }
                            items(group.entries) { entry ->
                                val m = entry.movement
                                val verb =
                                    when (m) {
                                        is StockMovement.Replenishment -> stringResource(Res.string.history_replenish)
                                        is StockMovement.Consumption -> stringResource(Res.string.history_consume)
                                        is StockMovement.Correction -> stringResource(Res.string.history_replenish) // 訂正は集計外だが行表示は補充扱いにしない場合は除外
                                    }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    PrimaryButton(onClick = { onOpenProduct(entry.product.id) }) {
                                        AppText(entry.product.name())
                                    }
                                    AppText(
                                        stringResource(
                                            Res.string.activity_row_summary,
                                            verb,
                                            m.quantity(),
                                            entry.product.setting.unit(),
                                            m.actor.profile.displayName(),
                                        ),
                                    )
                                    AppText(hm(m.occurredAt()))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun dayLabelText(label: DayLabel): String =
    when (label) {
        is DayLabel.Resource -> stringResource(label.resource)
        is DayLabel.NDaysAgo -> stringResource(label.resource, label.days)
        is DayLabel.Date -> label.iso
    }
```

注: 活動行で `Correction` をどう表示するか。モックは補充/消費のみを feed 行に出す想定（訂正は商品履歴側）。ActivityFeed が Correction を含む場合は行から除外するか、別ラベルにする。**実装方針: Activity 行は Replenishment/Consumption のみ表示し、Correction は除外**（`group.entries` を `filter { it.movement !is StockMovement.Correction }`、空になった group は出さない）。上のコードの `when` の Correction 分岐はこの filter で到達しなくなる。filter を `groupedByDay` の前段（ViewModel/Screen）で適用すること。

- [ ] **Step 4: InventoryRoute を簡素化（詳細/CorrectionSheet を除去、onOpenProduct を上げる）**

```kotlin
package net.brightroom.mindstock.frontend.feature.inventory.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.frontend.core.ui.InventoryRefreshController
import net.brightroom.mindstock.frontend.feature.inventory.InventoryViewModel

@Composable
fun InventoryRoute(
    homeViewModel: InventoryViewModel,
    refresh: InventoryRefreshController,
    onOpenProduct: (ProductId, Stock?) -> Unit,
    onAddProduct: () -> Unit,
    displayName: String = "",
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val state by homeViewModel.state.collectAsState()
    var moveTarget by remember { mutableStateOf<Pair<Stock, MoveMode>?>(null) }

    LaunchedEffect(Unit) { homeViewModel.load() }
    LaunchedEffect(refresh) { refresh.signal.collect { homeViewModel.load() } }

    StockHomeScreen(
        state = state,
        displayName = displayName,
        onSelectView = { homeViewModel.setView(it) },
        onQueryChange = { homeViewModel.setQuery(it) },
        onOpen = { stock -> onOpenProduct(stock.product.id, stock) },
        onReplenish = { moveTarget = it to MoveMode.Replenish },
        onConsume = { moveTarget = it to MoveMode.Consume },
        onAddProduct = onAddProduct,
        modifier = modifier,
    )

    val mt = moveTarget
    MoveSheet(
        open = mt != null,
        mode = mt?.second ?: MoveMode.Replenish,
        stock = mt?.first,
        onClose = { moveTarget = null },
        onSubmit = { quantity, note ->
            val (stock, mode) = mt ?: return@MoveSheet
            scope.launch {
                when (mode) {
                    MoveMode.Replenish -> homeViewModel.replenish(stock.product.id, Quantity(quantity), Note(note))
                    MoveMode.Consume -> homeViewModel.consume(stock.product.id, Quantity(quantity), Note(note))
                }
            }
            moveTarget = null
        },
    )
}
```

注: `StockHomeScreen` の `onOpen` シグネチャは `(Stock) -> Unit`。`ProductDetailViewModel`/`ProductDetailScreen` への参照は InventoryRoute から消える（オーバーレイへ移行）。

- [ ] **Step 5: AppShell にスロット追加**

`AppShell` を以下に変更（`shopContent`/`activityContent` を追加、Profile placeholder 据置）:

```kotlin
@Composable
fun AppShell(
    stockContent: @Composable () -> Unit,
    shopContent: @Composable () -> Unit,
    activityContent: @Composable () -> Unit,
) {
    var selected by remember { mutableStateOf(Tab.Stock) }
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            Tab.entries.forEach { tab ->
                item(
                    selected = tab == selected,
                    onClick = { selected = tab },
                    icon = { AppIcon(tab.icon, contentDescription = stringResource(tab.label)) },
                    label = { AppText(stringResource(tab.label)) },
                )
            }
        },
    ) {
        when (selected) {
            Tab.Stock -> stockContent()
            Tab.Shop -> shopContent()
            Tab.Activity -> activityContent()
            Tab.Profile -> AppText(stringResource(Res.string.tab_profile_placeholder))
        }
    }
}
```

`tab_shop_placeholder`/`tab_activity_placeholder` の import は不要になるので削除。

- [ ] **Step 6: App.kt を配線**

`App.kt` の `AuthState.Ready` 分岐を以下に変更（要点: `InventoryRefreshController` 生成、Shop/Activity VM 生成、overlay 状態、`onOpenProduct`、`ProductDetailOverlay` を Box 上層に描画）。Repository 生成に `productRegisterService` を追加。

Repository 生成（既存 remember ブロック）に追加:

```kotlin
        val repository =
            remember {
                InventoryRepository(
                    productService = { rpc.service<ProductRpcService>() },
                    stockService = { rpc.service<StockRpcService>() },
                    stockRegisterService = { rpc.service<StockRegisterRpcService>() },
                    productRegisterService = { rpc.service<ProductRegisterRpcService>() },
                )
            }
        val refresh = remember { InventoryRefreshController() }
```

`AuthState.Ready` の `householdId != null` ブロックを:

```kotlin
                    } else {
                        var opened by remember { mutableStateOf<DetailTarget?>(null) }
                        val homeVm =
                            remember(householdId) {
                                InventoryViewModel(
                                    householdId = householdId,
                                    loadStocks = repository::list,
                                    replenishStock = repository::replenish,
                                    consumeStock = repository::consume,
                                    refresh = refresh,
                                    toast = toast,
                                    reauth = reauth,
                                )
                            }
                        val shopVm =
                            remember(householdId) {
                                ShoppingListViewModel(
                                    householdId = householdId,
                                    loadShoppingList = repository::shoppingList,
                                    setWantedFlag = repository::setWanted,
                                    replenishStock = repository::replenish,
                                    refresh = refresh,
                                    toast = toast,
                                    reauth = reauth,
                                )
                            }
                        val activityVm =
                            remember(householdId) {
                                ActivityViewModel(
                                    householdId = householdId,
                                    loadActivity = repository::activity,
                                    toast = toast,
                                    reauth = reauth,
                                )
                            }
                        AppShell(
                            stockContent = {
                                InventoryRoute(
                                    homeViewModel = homeVm,
                                    refresh = refresh,
                                    onOpenProduct = { pid, seed -> opened = DetailTarget(pid, seed) },
                                    onAddProduct = { toast.show(UiText(Res.string.feature_coming_soon)) },
                                    displayName = sessionState.displayName?.invoke() ?: "",
                                )
                            },
                            shopContent = {
                                val shopState by shopVm.state.collectAsState()
                                LaunchedEffect(shopVm) { shopVm.load() }
                                LaunchedEffect(refresh) { refresh.signal.collect { shopVm.load() } }
                                ShoppingListScreen(
                                    state = shopState,
                                    onOpenProduct = { pid, seed -> opened = DetailTarget(pid, seed) },
                                    onSetWanted = { pid, w -> scope.launch { shopVm.setWanted(pid, w) } },
                                    onReplenish = { pid, q, n -> scope.launch { shopVm.replenish(pid, Quantity(q), Note(n)) } },
                                )
                            },
                            activityContent = {
                                val activityState by activityVm.state.collectAsState()
                                LaunchedEffect(activityVm) { activityVm.load() }
                                LaunchedEffect(refresh) { refresh.signal.collect { activityVm.load() } }
                                ActivityScreen(
                                    state = activityState,
                                    onOpenProduct = { pid -> opened = DetailTarget(pid, null) },
                                )
                            },
                        )
                        val target = opened
                        if (target != null) {
                            ProductDetailOverlay(
                                target = target,
                                viewModelFactory = { t ->
                                    ProductDetailViewModel(
                                        householdId = householdId,
                                        productId = t.productId,
                                        seed = t.seed,
                                        loadShoppingList = repository::shoppingList,
                                        loadHistory = repository::history,
                                        replenishStock = repository::replenish,
                                        consumeStock = repository::consume,
                                        correctMovement = repository::correct,
                                        setWantedFlag = repository::setWanted,
                                        refresh = refresh,
                                        toast = toast,
                                        reauth = reauth,
                                    )
                                },
                                refresh = refresh,
                                onBack = { opened = null },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
```

App.kt に追加 import:
- `androidx.compose.runtime.mutableStateOf` / `setValue`（既存に getValue あり）
- `androidx.compose.runtime.rememberCoroutineScope` と `val scope = rememberCoroutineScope()` を `MindstockTheme {` 直下に追加
- `kotlinx.coroutines.launch`
- `net.brightroom.mindstock.domain.model.inventory.quantity.Quantity`
- `net.brightroom.mindstock.domain.model.inventory.stock.movement.Note`
- `net.brightroom.mindstock.frontend.core.ui.InventoryRefreshController`
- `net.brightroom.mindstock.frontend.feature.inventory.ui.DetailTarget` / `ProductDetailOverlay`
- `net.brightroom.mindstock.frontend.feature.shopping.ShoppingListViewModel` / `ui.ShoppingListScreen`
- `net.brightroom.mindstock.frontend.feature.activity.ActivityViewModel` / `ui.ActivityScreen`
- `net.brightroom.mindstock.rpc.product.ProductRegisterRpcService`

`ProductDetailViewModel` の旧 import（`InventoryRoute` 用 factory）は不要になる箇所を整理。

- [ ] **Step 7: フルコンパイル**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL（コンパイルエラーがあれば import / シグネチャを修正。特に `MoveSheet`/`StockHomeScreen`/`Sheet`/`TextInput`/`Stepper`/`PrimaryButton` の実シグネチャに合わせる）

- [ ] **Step 8: 全テスト**

Run: `./gradlew :frontend:jsTest`
Expected: PASS（Task 2–8 のテストが全て通る）

- [ ] **Step 9: Commit**

```bash
git add frontend/src
git commit -m "feat(frontend): 買い物/活動タブ画面と ProductDetail オーバーレイを配線(setWanted/クロスタブ詳細)"
```

---

## Task 12: 最終検証

- [ ] **Step 1: コンパイル + テスト再実行**

Run: `./gradlew :frontend:compileKotlinWasmJs :frontend:jsTest`
Expected: 両方 SUCCESSFUL / PASS

- [ ] **Step 2: lint/format（あれば）**

Run: `./gradlew :frontend:ktlintCheck`（存在すれば。無ければスキップ）
Expected: PASS。失敗時は `ktlintFormat` で整形しコミット。

- [ ] **Step 3: 設計の自己点検**

- 買い物リスト: auto/manual 区分・進捗・追加シート・行 ✕（setWanted false）・行補充 ＝ 実装済みか。
- 活動: 日付グルーピング・hm・行タップ詳細・空状態 ＝ 実装済みか。
- 詳細: shoppingList 由来の Stock/wanted・wanted トグル 3 状態・mutation→refresh ＝ 実装済みか。
- クロスタブ: shop/activity 行タップで同一オーバーレイが開くか（productId 軸）。
- refresh: stock/shop の mutation が他タブへ波及するか（collect は各 route の LaunchedEffect）。

- [ ] **Step 4: live 検証は申し送り**

backend（Zitadel/Postgres）起動を要する live 検証・見た目の最終トーン調整は、実装が揃ってからユーザが実機で判断（`frontend-visual-fidelity-expectation` 方針）。`./gradlew :backend:api:run` + `:frontend:wasmJsBrowserDevelopmentRun`。

---

## Self-Review（プラン作成者による点検結果）

- **Spec coverage**: 共有 refresh(§1)=Task2,4 / Shop(§2)=Task5,11 / Activity(§3)=Task6,7,11 / Detail 昇格(§4)=Task8,9,10,11 / AppShell・App(§5)=Task11 / i18n(§6)=Task1 / テスト(§6)=各 Task。全節カバー。
- **Placeholder scan**: TBD/TODO なし。UI スタイル詳細は既存 atom + token に委ねる旨を明記（曖昧表現ではなく既存パターン参照）。
- **Type consistency**: `InventoryRefreshController.request()/signal`、`ShoppingListViewModel(householdId, loadShoppingList, setWantedFlag, replenishStock, refresh, toast, reauth)`、`ProductDetailViewModel(householdId, productId, seed, loadShoppingList, loadHistory, replenishStock, consumeStock, correctMovement, setWantedFlag, refresh, toast, reauth)`、`ProductDetailScreen(detail, seed, onBack, onReplenish, onConsume, onCorrect, onToggleWanted)`、`DetailTarget(productId, seed)` をタスク間で一貫使用。
- **既知の実装時注意**: (a) ShoppingListScreen の `onReplenish` は `(ProductId, Int, String)` に統一（数量フロー、Task11 Step1 注）。(b) Activity 行は Correction を除外（Task11 Step3 注）。(c) 各 atom（MoveSheet/Sheet/TextInput/Stepper/PrimaryButton/StockHomeScreen.onOpen）の実シグネチャに合わせる。(d) Task8 のテストは Task11 までコンパイルが揃わないため、`jsTest` グリーン確認は Task11 Step8 でまとめて行う。
