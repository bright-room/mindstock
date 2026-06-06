# P6-1a: 在庫まわり frontend スパイン Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** P6-0 の placeholder を実画面に差し替え、在庫ホーム・商品詳細・補充/消費/訂正・商品履歴を live に配線する（spec の P6-1a フェーズ）。

**Architecture:** boot で `household.list()` を取得し `AppSession` にアクティブ世帯を確定 → `App()` が live な `StockHomeScreen` をレンダ。ViewModel は関数型依存（既存パターン）で `InventoryRepository` のメソッドを受け、書込成功後に再フェッチ。エラーは `UiText`（StringResource ラッパ）で i18n 化し、`Conflict`/`Internal`/`NotFound` はトースト、`Unauthorized` は単一の再認証導線へ。新規 atom（Sheet/Stepper/Thumb/RoundBtn/Toast）は designsystem に封じ込め。

**Tech Stack:** Kotlin Multiplatform（js + wasmJs）、Compose Multiplatform、Material3 Expressive（designsystem 封じ込め）、kotlinx-rpc client、Compose Resources（i18n）、kotlin.test + Kotest assertions（commonTest）。

**検証コマンド:**
- コンパイル: `./gradlew :frontend:compileKotlinWasmJs`（フルビルドはローカル OOM しうる）
- テスト: `./gradlew :frontend:jsTest`（`--tests` フィルタ非対応＝全体実行）

**ブランチ:** `feat/p6-1a-inventory-frontend-spine`（worktree 推奨）

**スコープ外（P6-1b / 別プラン）:** 買い物リストタブ、活動(activity)タブ、`setWanted`（商品詳細の wanted トグル）。商品追加/設定/アーカイブは P6-2、世帯切替/作成・オンボーディングは P6-3。

---

## ファイル構成

**新規（core / designsystem 基盤）:**
- `frontend/src/commonMain/.../core/ui/UiText.kt` — StringResource + args の i18n ラッパ
- `frontend/src/commonMain/.../core/ui/ToastController.kt` — トースト発火（`StateFlow<ToastMessage?>`）
- `frontend/src/commonMain/.../core/auth/ReauthController.kt` — 再認証シグナル（`SharedFlow<Unit>`）
- `frontend/src/commonMain/.../designsystem/atom/Toast.kt` — Snackbar 風表示ホスト
- `frontend/src/commonMain/.../designsystem/atom/Sheet.kt` — モーダルボトムシート
- `frontend/src/commonMain/.../designsystem/atom/Stepper.kt` — 数量 ± stepper
- `frontend/src/commonMain/.../designsystem/atom/Thumb.kt` — 商品サムネ
- `frontend/src/commonMain/.../designsystem/atom/RoundBtn.kt` — 円形アイコンボタン

**新規（feature/inventory）:**
- `frontend/src/commonMain/.../feature/inventory/ui/ProductCard.kt` — list/grid カード
- `frontend/src/commonMain/.../feature/inventory/ui/MoveSheet.kt` — 補充/消費シート
- `frontend/src/commonMain/.../feature/inventory/ui/ProductDetailScreen.kt` — 詳細 + 履歴 + 訂正
- `frontend/src/commonMain/.../feature/inventory/ProductDetailViewModel.kt`
- `frontend/src/commonMain/.../feature/inventory/ProductDetailUiState.kt`
- `frontend/src/commonMain/.../feature/inventory/ui/InventoryRoute.kt` — home+detail 状態の束ね（live エントリ）
- `frontend/src/commonTest/.../feature/inventory/ProductDetailViewModelTest.kt`
- `frontend/src/commonTest/.../core/rpc/UiTextErrorTest.kt`

**修正:**
- `frontend/src/commonMain/.../core/rpc/RpcErrors.kt` — `userMessageOf` → `errorText(RpcError): UiText`、`requiresReauth` 維持
- `frontend/src/commonMain/.../core/auth/AuthState.kt` — `NeedHousehold` 追加
- `frontend/src/commonMain/.../app/AuthViewModel.kt` — boot で `loadHouseholds`
- `frontend/src/webMain/.../WebAuthDeps.kt` — `loadHouseholds` 実装
- `frontend/src/commonMain/.../feature/inventory/InventoryViewModel.kt` — 検索/補充/消費/再フェッチ/toast/reauth/detail open
- `frontend/src/commonMain/.../feature/inventory/InventoryUiState.kt` — `Error(message)` → `Error(UiText)`、検索/詳細状態
- `frontend/src/commonMain/.../feature/inventory/data/InventoryRepository.kt` — `stockService` 追加・`history`/`correct`
- `frontend/src/commonMain/.../feature/inventory/ui/StockHomeScreen.kt` — 実カード/検索/quick action へ作り込み
- `frontend/src/commonMain/.../designsystem/atom/StockLevelBar.kt` — `trackColor` 引数
- `frontend/src/commonMain/.../app/shell/AppShell.kt` — stockContent を live route に
- `frontend/src/webMain/.../App.kt` — live 配線・toast host・reauth 受け口・NeedHousehold
- `frontend/src/commonMain/composeResources/values/strings.xml` — 文言追加
- `frontend/src/commonTest/.../app/AuthViewModelTest.kt` — household load 分岐
- `frontend/src/commonTest/.../feature/inventory/InventoryViewModelTest.kt` — 既存テストの `Error` 型変更追従 + 新規

---

## Task 1: UiText（i18n ラッパ）

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/ui/UiText.kt`
- Test: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/core/rpc/UiTextErrorTest.kt`（Task 2 で内容追加。本タスクでは UiText の等価性のみ）

非 Composable 層（ViewModel）が文言を「リソースキー + 引数」として持ち回り、UI で解決する seam。spec §7 の暫定例外縮小。

- [ ] **Step 1: UiText を実装**

```kotlin
package net.brightroom.mindstock.frontend.core.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** 非 Composable 層が持ち回るユーザ向け文言（リソースキー + 書式引数）。UI 層で resolve する。 */
data class UiText(
    val resource: StringResource,
    val args: List<String> = emptyList(),
)

@Composable
fun UiText.resolve(): String =
    if (args.isEmpty()) {
        stringResource(resource)
    } else {
        stringResource(resource, *args.toTypedArray())
    }
```

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/ui/UiText.kt
git commit -m "feat(frontend): add UiText i18n wrapper for non-composable layers"
```

---

## Task 2: errorText（RpcError → UiText）

**Files:**
- Modify: `frontend/src/commonMain/.../core/rpc/RpcErrors.kt`
- Modify: `frontend/src/commonMain/composeResources/values/strings.xml`
- Test: `frontend/src/commonTest/.../core/rpc/UiTextErrorTest.kt`

`userMessageOf(RpcError): String`（literal）を `errorText(RpcError): UiText`（リソース）に置換。`Unauthorized` は再認証導線で処理するため文言は汎用で残す（呼び出し側が requiresReauth を先に見る）。

- [ ] **Step 1: strings.xml にエラー文言を追加**

`<resources>` 内（既存の `error_generic` の隣）に追記:

```xml
    <string name="error_unauthorized">セッションが切れました。再ログインしてください。</string>
    <string name="error_not_found">対象が見つかりませんでした。</string>
    <string name="error_bad_request">入力に誤りがあります: %1$s</string>
    <string name="error_conflict">操作が競合しました: %1$s</string>
    <string name="error_internal">サーバでエラーが発生しました。</string>
    <string name="toast_replenished">補充しました</string>
    <string name="toast_consumed">消費しました</string>
    <string name="toast_corrected">訂正を記録しました</string>
```

- [ ] **Step 2: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.frontend.core.rpc

import io.kotest.matchers.shouldBe
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.error_bad_request
import mindstock.frontend.generated.resources.error_conflict
import mindstock.frontend.generated.resources.error_internal
import mindstock.frontend.generated.resources.error_not_found
import mindstock.frontend.generated.resources.error_unauthorized
import net.brightroom.mindstock.rpc.result.RpcError
import kotlin.test.Test

class UiTextErrorTest {
    @Test
    fun maps_each_variant_to_resource_and_args() {
        errorText(RpcError.Unauthorized("x")).resource shouldBe Res.string.error_unauthorized
        errorText(RpcError.NotFound("x")).resource shouldBe Res.string.error_not_found
        errorText(RpcError.Internal("x")).resource shouldBe Res.string.error_internal

        val bad = errorText(RpcError.BadRequest("qty", "too big"))
        bad.resource shouldBe Res.string.error_bad_request
        bad.args shouldBe listOf("too big")

        val conflict = errorText(RpcError.Conflict("insufficient"))
        conflict.resource shouldBe Res.string.error_conflict
        conflict.args shouldBe listOf("insufficient")
    }

    @Test
    fun requires_reauth_only_for_unauthorized() {
        RpcError.Unauthorized("x").requiresReauth() shouldBe true
        RpcError.NotFound("x").requiresReauth() shouldBe false
    }
}
```

> 確定済み（`rpc/.../result/RpcError.kt`）: `Unauthorized(reason)` / `NotFound(message)` / `BadRequest(field, reason)` / `Conflict(reason)` / `Internal(reason)` はすべて **data class**（object ではない）。`errorText` の `BadRequest` は `reason` のみ args に入れる（`field` は UI 表示には使わない）。

- [ ] **Step 3: テストが失敗することを確認**

Run: `./gradlew :frontend:jsTest`
Expected: FAIL（`errorText` 未定義）

- [ ] **Step 4: RpcErrors.kt を実装（userMessageOf を置換）**

```kotlin
package net.brightroom.mindstock.frontend.core.rpc

import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.error_bad_request
import mindstock.frontend.generated.resources.error_conflict
import mindstock.frontend.generated.resources.error_internal
import mindstock.frontend.generated.resources.error_not_found
import mindstock.frontend.generated.resources.error_unauthorized
import net.brightroom.mindstock.frontend.core.ui.UiText
import net.brightroom.mindstock.rpc.result.RpcError

/** RpcError variant を網羅し UiText（リソース）に変換。新 variant 追加でコンパイルエラー。 */
fun errorText(error: RpcError): UiText =
    when (error) {
        is RpcError.Unauthorized -> UiText(Res.string.error_unauthorized)
        is RpcError.NotFound -> UiText(Res.string.error_not_found)
        is RpcError.BadRequest -> UiText(Res.string.error_bad_request, listOf(error.reason))
        is RpcError.Conflict -> UiText(Res.string.error_conflict, listOf(error.reason))
        is RpcError.Internal -> UiText(Res.string.error_internal)
    }

/** 再認証が必要なエラーか（呼び出し側が token 破棄 → authorize へ倒す判定）。 */
fun RpcError.requiresReauth(): Boolean = this is RpcError.Unauthorized
```

- [ ] **Step 5: テストが通ることを確認**

Run: `./gradlew :frontend:jsTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/rpc/RpcErrors.kt \
        frontend/src/commonMain/composeResources/values/strings.xml \
        frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/core/rpc/UiTextErrorTest.kt
git commit -m "feat(frontend): errorText maps RpcError to UiText resources"
```

---

## Task 3: ToastController

**Files:**
- Create: `frontend/src/commonMain/.../core/ui/ToastController.kt`
- Test: `frontend/src/commonTest/.../core/ui/ToastControllerTest.kt`

feature から発火し app 層のホストが描画する単一トーストチャネル。

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.frontend.core.ui

import io.kotest.matchers.shouldBe
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.toast_replenished
import kotlin.test.Test

class ToastControllerTest {
    @Test
    fun show_publishes_message_and_dismiss_clears() {
        val controller = ToastController()
        controller.current.value shouldBe null
        controller.show(UiText(Res.string.toast_replenished))
        controller.current.value?.text?.resource shouldBe Res.string.toast_replenished
        controller.dismiss()
        controller.current.value shouldBe null
    }
}
```

- [ ] **Step 2: 失敗確認**

Run: `./gradlew :frontend:jsTest`
Expected: FAIL（`ToastController` 未定義）

- [ ] **Step 3: 実装**

```kotlin
package net.brightroom.mindstock.frontend.core.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** トーストメッセージ。文言は UiText（UI 層で resolve）。 */
data class ToastMessage(
    val text: UiText,
)

/** 単一トーストチャネル。feature が show、app 層ホストが購読して描画。 */
class ToastController {
    private val _current = MutableStateFlow<ToastMessage?>(null)
    val current: StateFlow<ToastMessage?> = _current.asStateFlow()

    fun show(text: UiText) {
        _current.value = ToastMessage(text)
    }

    fun dismiss() {
        _current.value = null
    }
}
```

- [ ] **Step 4: テスト通過確認**

Run: `./gradlew :frontend:jsTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/ui/ToastController.kt \
        frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/core/ui/ToastControllerTest.kt
git commit -m "feat(frontend): add ToastController single-channel toast state"
```

---

## Task 4: ReauthController（再認証シグナル）

**Files:**
- Create: `frontend/src/commonMain/.../core/auth/ReauthController.kt`
- Test: `frontend/src/commonTest/.../core/auth/ReauthControllerTest.kt`

feature ViewModel は「再認証が要る」を発火するだけ。app 受け口が token 破棄 → closeAll → authorize を実行（Task 14）。

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.frontend.core.auth

import io.kotest.matchers.collections.shouldHaveSize
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ReauthControllerTest {
    @Test
    fun request_emits_signal() =
        runTest {
            val controller = ReauthController()
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

> `runCurrent` は `kotlinx.coroutines.test.runCurrent`（`runTest` の `TestScope` 拡張）。import を追加すること。

- [ ] **Step 2: 失敗確認**

Run: `./gradlew :frontend:jsTest`
Expected: FAIL（`ReauthController` 未定義）

- [ ] **Step 3: 実装**

```kotlin
package net.brightroom.mindstock.frontend.core.auth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 再認証要求の単一シグナル。feature が request、app 受け口が collect して token 破棄→authorize。 */
class ReauthController {
    private val _signal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val signal: SharedFlow<Unit> = _signal.asSharedFlow()

    fun request() {
        _signal.tryEmit(Unit)
    }
}
```

- [ ] **Step 4: テスト通過確認**

Run: `./gradlew :frontend:jsTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/auth/ReauthController.kt \
        frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/core/auth/ReauthControllerTest.kt
git commit -m "feat(frontend): add ReauthController reauth signal"
```

---

## Task 5: AuthState.NeedHousehold + boot で household.list()

**Files:**
- Modify: `frontend/src/commonMain/.../core/auth/AuthState.kt`
- Modify: `frontend/src/commonMain/.../app/AuthViewModel.kt`
- Test: `frontend/src/commonTest/.../app/AuthViewModelTest.kt`

boot の Registered 分岐で世帯をロードし、空なら `NeedHousehold`、非空なら先頭をアクティブにして `Ready`。

- [ ] **Step 1: AuthState に NeedHousehold を追加**

`AuthState.kt` の sealed interface に追記:

```kotlin
    /** 認証済み・登録済みだが所属世帯ゼロ。世帯作成へ（P6-3 で本実装）。 */
    data object NeedHousehold : AuthState
```

- [ ] **Step 2: 失敗するテストを書く（AuthViewModelTest に追記）**

`FakeAuthDeps` に世帯ロードを足し、テスト 2 本を追加。`FakeAuthDeps` を以下に差し替え（`households` パラメータ追加、`loadHouseholds` 実装）:

```kotlin
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.Households
import net.brightroom.mindstock.domain.model.household.Profile as HouseholdProfile
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.member.Members
```

`FakeAuthDeps` に追加:

```kotlin
    private val households: Households = Households(emptyList())
    var setHouseholdsCalled: Households? = null

    override suspend fun loadHouseholds(): Households = households

    override fun onHouseholdsLoaded(households: Households, active: HouseholdId) {
        setHouseholdsCalled = households
    }
```

テスト追加:

```kotlin
    @Test
    fun registered_with_household_becomes_ready() =
        runTest {
            val resident = Resident(ResidentId.create(), Profile(DisplayName("name")))
            val hh = Household(HouseholdId.create(), HouseholdProfile(HouseholdName("家")), Members(emptyList()))
            val deps =
                FakeAuthDeps(
                    path = "/", token = "tok",
                    status = SessionStatus.Registered(resident),
                    households = Households(listOf(hh)),
                )
            val vm = AuthViewModel(deps)
            vm.boot()
            vm.state.value.shouldBeInstanceOf<AuthState.Ready>()
            deps.setHouseholdsCalled?.size() shouldBe 1
        }

    @Test
    fun registered_without_household_becomes_need_household() =
        runTest {
            val resident = Resident(ResidentId.create(), Profile(DisplayName("name")))
            val deps =
                FakeAuthDeps(
                    path = "/", token = "tok",
                    status = SessionStatus.Registered(resident),
                    households = Households(emptyList()),
                )
            val vm = AuthViewModel(deps)
            vm.boot()
            vm.state.value.shouldBeInstanceOf<AuthState.NeedHousehold>()
        }
```

> `FakeAuthDeps` のコンストラクタに `private val households: Households = Households(emptyList())` を実引数として渡せるよう、コンストラクタ引数へ移すこと（上記は説明のため body に書いたが、実際は primary constructor パラメータにする）。既存テストは `households` 省略でデフォルト空＝`NeedHousehold` になるため、`registered_becomes_ready` 既存テストは `households = Households(listOf(hh))` を渡すよう修正する。

- [ ] **Step 3: 失敗確認**

Run: `./gradlew :frontend:jsTest`
Expected: FAIL（`AuthDeps.loadHouseholds`/`onHouseholdsLoaded` 未定義）

- [ ] **Step 4: AuthDeps と AuthViewModel.boot を実装**

`AuthViewModel.kt` の `AuthDeps` に追加:

```kotlin
    /** 所属世帯一覧をロード（whoami=Registered 後）。失敗時 throw。 */
    suspend fun loadHouseholds(): Households

    /** ロードした世帯と先頭アクティブをセッションに反映。 */
    fun onHouseholdsLoaded(households: Households, active: HouseholdId)
```

import 追加: `import net.brightroom.mindstock.domain.model.household.Households`

`boot()` の `SessionStatus.Registered` 分岐を差し替え:

```kotlin
                is SessionStatus.Registered -> {
                    deps.onAuthenticated(status.resident)
                    val households = deps.loadHouseholds()
                    val first = households.list.firstOrNull()
                    if (first == null) {
                        _state.value = AuthState.NeedHousehold
                    } else {
                        deps.onHouseholdsLoaded(households, first.id)
                        _state.value = AuthState.Ready
                    }
                }
```

- [ ] **Step 5: テスト通過確認**

Run: `./gradlew :frontend:jsTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/auth/AuthState.kt \
        frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/AuthViewModel.kt \
        frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/app/AuthViewModelTest.kt
git commit -m "feat(frontend): load households on boot, add NeedHousehold state"
```

---

## Task 6: WebAuthDeps に loadHouseholds / onHouseholdsLoaded 実装

**Files:**
- Modify: `frontend/src/webMain/.../WebAuthDeps.kt`

webMain はテスト無し（jvm ターゲット無し）。コンパイル確認のみ。

- [ ] **Step 1: WebAuthDeps に実装を追加**

import 追加:

```kotlin
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.Households
import net.brightroom.mindstock.rpc.household.HouseholdRpcService
```

メソッド追加（`onAuthenticated` の近く）:

```kotlin
    override suspend fun loadHouseholds(): Households =
        when (val r = rpc.service<HouseholdRpcService>().list()) {
            is RpcResult.Ok -> r.value
            is RpcResult.Err -> error("household list failed: ${r.error}")
        }

    override fun onHouseholdsLoaded(households: Households, active: HouseholdId) {
        session.setHouseholds(households, active)
    }
```

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/WebAuthDeps.kt
git commit -m "feat(frontend): WebAuthDeps loads households via HouseholdRpcService"
```

---

## Task 7: InventoryRepository 拡張（stockService / history / correct）

**Files:**
- Modify: `frontend/src/commonMain/.../feature/inventory/data/InventoryRepository.kt`
- Test: `frontend/src/commonTest/.../feature/inventory/data/InventoryRepositoryTest.kt`（既存があれば追記）

`StockRpcService`（history）を足し、`correct` を追加。`replenish`/`consume`/`list` は維持。

> 既存 `InventoryRepositoryTest.kt` は `@Rpc` interface の fake を要し commonTest で重い。本タスクのテストは「`toOutcome` 変換が正しく素通る」最小確認に留め、深い検証は ViewModel テスト（関数型注入）で行う。既存テストが無ければ Step 1-3 はスキップし実装＋コンパイルのみ。

- [ ] **Step 1: InventoryRepository を実装**

```kotlin
package net.brightroom.mindstock.frontend.feature.inventory.data

import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Reason
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.toOutcome
import net.brightroom.mindstock.rpc.product.ProductRpcService
import net.brightroom.mindstock.rpc.stock.StockRegisterRpcService
import net.brightroom.mindstock.rpc.stock.StockRpcService

/**
 * 在庫まわりの RPC を隠蔽。サービスは「開く関数」を遅延注入（認証後にトークン付きで open される）。
 */
class InventoryRepository(
    private val productService: () -> ProductRpcService,
    private val stockService: () -> StockRpcService,
    private val stockRegisterService: () -> StockRegisterRpcService,
) {
    suspend fun list(householdId: HouseholdId): RpcOutcome<Stocks> = productService().list(householdId).toOutcome()

    suspend fun history(productId: ProductId): RpcOutcome<StockMovements> = stockService().history(productId).toOutcome()

    suspend fun replenish(productId: ProductId, quantity: Quantity, note: Note): RpcOutcome<Unit> =
        stockRegisterService().replenish(productId, quantity, note).toOutcome()

    suspend fun consume(productId: ProductId, quantity: Quantity, note: Note): RpcOutcome<Unit> =
        stockRegisterService().consume(productId, quantity, note).toOutcome()

    suspend fun correct(target: MovementId, correctedQuantity: Quantity, reason: Reason): RpcOutcome<Unit> =
        stockRegisterService().correct(target, correctedQuantity, reason).toOutcome()
}
```

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL（既存 `InventoryRepositoryTest` がコンストラクタ変更で壊れる場合は `stockService = { ... }` を足して修正）

- [ ] **Step 3: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/data/InventoryRepository.kt
git commit -m "feat(frontend): InventoryRepository adds history and correct"
```

---

## Task 8: InventoryUiState を拡張（UiText / 検索 / 詳細選択）

**Files:**
- Modify: `frontend/src/commonMain/.../feature/inventory/InventoryUiState.kt`

`Error(message: String)` を `Error(text: UiText)` に変更し、Content に検索クエリを追加。

- [ ] **Step 1: 実装**

```kotlin
package net.brightroom.mindstock.frontend.feature.inventory

import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.frontend.core.ui.UiText

sealed interface InventoryUiState {
    data object Loading : InventoryUiState

    data class Content(
        val stocks: Stocks,
        val view: StockView,
        val query: String = "",
    ) : InventoryUiState {
        /** query で名前 substring 絞り込み（frontend 側フィルタ）。 */
        fun visibleStocks(): Stocks {
            val q = query.trim()
            if (q.isEmpty()) return stocks
            return Stocks(stocks.list.filter { it.product.name().contains(q, ignoreCase = true) })
        }
    }

    data class Error(
        val text: UiText,
    ) : InventoryUiState
}
```

- [ ] **Step 2: コンパイル確認（この時点で ViewModel/Screen が未追従なら次タスクで直す）**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: 失敗しうる（`InventoryViewModel`/`StockHomeScreen` が `Error(String)`/`copy(view=)` を参照）。Task 9/11 で追従。本タスクは UiState 変更のみコミット。

- [ ] **Step 3: Commit（コンパイルは次タスクとセットで緑化）**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/InventoryUiState.kt
git commit -m "refactor(frontend): InventoryUiState uses UiText and search query"
```

---

## Task 9: InventoryViewModel 拡張（検索 / 補充 / 消費 / 再フェッチ / toast / reauth / 詳細選択）

**Files:**
- Modify: `frontend/src/commonMain/.../feature/inventory/InventoryViewModel.kt`
- Test: `frontend/src/commonTest/.../feature/inventory/InventoryViewModelTest.kt`

関数型依存（既存パターン）。書込成功で `list()` 再フェッチ＋成功トースト、`Unauthorized` で reauth、他エラーはトースト。検索クエリと選択商品（詳細遷移）を保持。

- [ ] **Step 1: 失敗するテストを書く（既存テストを置換・拡張）**

```kotlin
package net.brightroom.mindstock.frontend.feature.inventory

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.toast_replenished
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.rpc.result.RpcError
import kotlin.test.Test

private fun vm(
    loadStocks: suspend (HouseholdId) -> RpcOutcome<Stocks> = { RpcOutcome.Success(Stocks(emptyList())) },
    replenish: suspend (ProductId, Quantity, Note) -> RpcOutcome<Unit> = { _, _, _ -> RpcOutcome.Success(Unit) },
    consume: suspend (ProductId, Quantity, Note) -> RpcOutcome<Unit> = { _, _, _ -> RpcOutcome.Success(Unit) },
    toast: ToastController = ToastController(),
    reauth: ReauthController = ReauthController(),
) = InventoryViewModel(
    householdId = HouseholdId.create(),
    loadStocks = loadStocks,
    replenishStock = replenish,
    consumeStock = consume,
    toast = toast,
    reauth = reauth,
)

class InventoryViewModelTest {
    @Test
    fun load_success_sets_content() =
        runTest {
            val v = vm()
            v.load()
            v.state.value.shouldBeInstanceOf<InventoryUiState.Content>()
        }

    @Test
    fun load_failure_sets_error() =
        runTest {
            val v = vm(loadStocks = { RpcOutcome.Failure(RpcError.Internal("boom")) })
            v.load()
            v.state.value.shouldBeInstanceOf<InventoryUiState.Error>()
        }

    @Test
    fun replenish_success_refetches_and_toasts() =
        runTest {
            var loads = 0
            val toast = ToastController()
            val v = vm(loadStocks = { loads++; RpcOutcome.Success(Stocks(emptyList())) }, toast = toast)
            v.load()
            v.replenish(ProductId.create(), Quantity(2), Note(""))
            loads shouldBe 2 // 初回 + 補充後の再フェッチ
            toast.current.value?.text?.resource shouldBe Res.string.toast_replenished
        }

    @Test
    fun unauthorized_on_write_requests_reauth() =
        runTest {
            var reauthRequested = 0
            val reauth = ReauthController()
            val job = kotlinx.coroutines.launch { reauth.signal.collect { reauthRequested++ } }
            kotlinx.coroutines.test.runCurrent()
            val v = vm(replenish = { _, _, _ -> RpcOutcome.Failure(RpcError.Unauthorized("expired")) }, reauth = reauth)
            v.load()
            v.replenish(ProductId.create(), Quantity(1), Note(""))
            kotlinx.coroutines.test.runCurrent()
            reauthRequested shouldBe 1
            job.cancel()
        }

    @Test
    fun search_filters_visible_stocks() =
        runTest {
            val v = vm()
            v.load()
            v.setQuery("xyz")
            val content = v.state.value as InventoryUiState.Content
            content.query shouldBe "xyz"
        }

    @Test
    fun query_survives_reload_after_write() =
        runTest {
            val v = vm()
            v.load()
            v.setQuery("milk")
            v.replenish(ProductId.create(), Quantity(1), Note("")) // 内部で load() 再フェッチ
            val content = v.state.value as InventoryUiState.Content
            content.query shouldBe "milk" // クエリが消えない
        }
}
```

- [ ] **Step 2: 失敗確認**

Run: `./gradlew :frontend:jsTest`
Expected: FAIL（新コンストラクタ/メソッド未定義）

- [ ] **Step 3: InventoryViewModel を実装**

```kotlin
package net.brightroom.mindstock.frontend.feature.inventory

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.toast_consumed
import mindstock.frontend.generated.resources.toast_replenished
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.errorText
import net.brightroom.mindstock.frontend.core.rpc.requiresReauth
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.frontend.core.ui.UiText

class InventoryViewModel(
    private val householdId: HouseholdId,
    private val loadStocks: suspend (HouseholdId) -> RpcOutcome<Stocks>,
    private val replenishStock: suspend (ProductId, Quantity, Note) -> RpcOutcome<Unit>,
    private val consumeStock: suspend (ProductId, Quantity, Note) -> RpcOutcome<Unit>,
    private val toast: ToastController,
    private val reauth: ReauthController,
) : ViewModel() {
    private val _state = MutableStateFlow<InventoryUiState>(InventoryUiState.Loading)
    val state: StateFlow<InventoryUiState> = _state.asStateFlow()

    // view / query は load() の再フェッチ（補充消費後）でも保持するため独立した source of truth に持つ。
    private val _view = MutableStateFlow(StockView.List)
    val view: StateFlow<StockView> = _view.asStateFlow()

    private val _query = MutableStateFlow("")

    suspend fun load() {
        _state.value = InventoryUiState.Loading
        _state.value =
            when (val out = loadStocks(householdId)) {
                is RpcOutcome.Success -> InventoryUiState.Content(out.value, _view.value, _query.value)
                is RpcOutcome.Failure -> {
                    handleFailure(out.error)
                    InventoryUiState.Error(errorText(out.error))
                }
            }
    }

    fun setView(v: StockView) {
        _view.value = v
        val s = _state.value
        if (s is InventoryUiState.Content) _state.value = s.copy(view = v)
    }

    fun setQuery(query: String) {
        _query.value = query
        val s = _state.value
        if (s is InventoryUiState.Content) _state.value = s.copy(query = query)
    }

    suspend fun replenish(productId: ProductId, quantity: Quantity, note: Note) =
        write(replenishStock(productId, quantity, note), UiText(Res.string.toast_replenished))

    suspend fun consume(productId: ProductId, quantity: Quantity, note: Note) =
        write(consumeStock(productId, quantity, note), UiText(Res.string.toast_consumed))

    private suspend fun write(outcome: RpcOutcome<Unit>, successText: UiText) {
        when (outcome) {
            is RpcOutcome.Success -> {
                load() // append-only のサーバ真実を再取得
                toast.show(successText)
            }
            is RpcOutcome.Failure -> handleFailure(outcome.error)
        }
    }

    private fun handleFailure(error: net.brightroom.mindstock.rpc.result.RpcError) {
        if (error.requiresReauth()) {
            reauth.request()
        } else {
            toast.show(errorText(error))
        }
    }
}
```

> 注: `load()` の Failure 時は `handleFailure` でトースト/reauth を出しつつ `Error` 状態も返す（一覧ロード失敗は画面エラー、書込失敗はトーストのみ、という spec §3.2 の使い分け）。`Unauthorized` の場合 reauth が走るので Error 文言は表示前にリダイレクトされる想定。

- [ ] **Step 4: テスト通過確認**

Run: `./gradlew :frontend:jsTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/InventoryViewModel.kt \
        frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/inventory/InventoryViewModelTest.kt
git commit -m "feat(frontend): InventoryViewModel search, replenish/consume with refetch+toast+reauth"
```

---

## Task 10: ProductDetailUiState + ProductDetailViewModel

**Files:**
- Create: `frontend/src/commonMain/.../feature/inventory/ProductDetailUiState.kt`
- Create: `frontend/src/commonMain/.../feature/inventory/ProductDetailViewModel.kt`
- Test: `frontend/src/commonTest/.../feature/inventory/ProductDetailViewModelTest.kt`

詳細画面用。`history()` ロード、`correct()`／補充消費（再フェッチ＝history 再取得）、toast、reauth。

- [ ] **Step 1: ProductDetailUiState を実装**

```kotlin
package net.brightroom.mindstock.frontend.feature.inventory

import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.frontend.core.ui.UiText

sealed interface ProductDetailUiState {
    data object Loading : ProductDetailUiState

    data class Content(
        val movements: StockMovements,
    ) : ProductDetailUiState

    data class Error(
        val text: UiText,
    ) : ProductDetailUiState
}
```

- [ ] **Step 2: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.frontend.feature.inventory

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.toast_corrected
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Reason
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.ui.ToastController
import kotlin.test.Test

private fun detailVm(
    loadHistory: suspend (ProductId) -> RpcOutcome<StockMovements> = { RpcOutcome.Success(StockMovements(emptyList())) },
    correct: suspend (MovementId, Quantity, Reason) -> RpcOutcome<Unit> = { _, _, _ -> RpcOutcome.Success(Unit) },
    toast: ToastController = ToastController(),
    reauth: ReauthController = ReauthController(),
) = ProductDetailViewModel(
    productId = ProductId.create(),
    loadHistory = loadHistory,
    correctMovement = correct,
    toast = toast,
    reauth = reauth,
)

class ProductDetailViewModelTest {
    @Test
    fun load_success_sets_content() =
        runTest {
            val v = detailVm()
            v.load()
            v.state.value.shouldBeInstanceOf<ProductDetailUiState.Content>()
        }

    @Test
    fun correct_success_refetches_and_toasts() =
        runTest {
            var loads = 0
            val toast = ToastController()
            val v = detailVm(loadHistory = { loads++; RpcOutcome.Success(StockMovements(emptyList())) }, toast = toast)
            v.load()
            v.correct(MovementId(1), Quantity(3), Reason("数え間違い"))
            loads shouldBe 2
            toast.current.value?.text?.resource shouldBe Res.string.toast_corrected
        }
}
```

- [ ] **Step 3: 失敗確認**

Run: `./gradlew :frontend:jsTest`
Expected: FAIL（`ProductDetailViewModel` 未定義）

- [ ] **Step 4: ProductDetailViewModel を実装**

```kotlin
package net.brightroom.mindstock.frontend.feature.inventory

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.toast_corrected
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Reason
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.errorText
import net.brightroom.mindstock.frontend.core.rpc.requiresReauth
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.frontend.core.ui.UiText
import net.brightroom.mindstock.rpc.result.RpcError

class ProductDetailViewModel(
    private val productId: ProductId,
    private val loadHistory: suspend (ProductId) -> RpcOutcome<StockMovements>,
    private val correctMovement: suspend (MovementId, Quantity, Reason) -> RpcOutcome<Unit>,
    private val toast: ToastController,
    private val reauth: ReauthController,
) : ViewModel() {
    private val _state = MutableStateFlow<ProductDetailUiState>(ProductDetailUiState.Loading)
    val state: StateFlow<ProductDetailUiState> = _state.asStateFlow()

    suspend fun load() {
        _state.value = ProductDetailUiState.Loading
        _state.value =
            when (val out = loadHistory(productId)) {
                is RpcOutcome.Success -> ProductDetailUiState.Content(out.value)
                is RpcOutcome.Failure -> {
                    handleFailure(out.error)
                    ProductDetailUiState.Error(errorText(out.error))
                }
            }
    }

    suspend fun correct(target: MovementId, correctedQuantity: Quantity, reason: Reason) {
        when (val out = correctMovement(target, correctedQuantity, reason)) {
            is RpcOutcome.Success -> {
                load()
                toast.show(UiText(Res.string.toast_corrected))
            }
            is RpcOutcome.Failure -> handleFailure(out.error)
        }
    }

    private fun handleFailure(error: RpcError) {
        if (error.requiresReauth()) reauth.request() else toast.show(errorText(error))
    }
}
```

- [ ] **Step 5: テスト通過確認**

Run: `./gradlew :frontend:jsTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ProductDetailUiState.kt \
        frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ProductDetailViewModel.kt \
        frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ProductDetailViewModelTest.kt
git commit -m "feat(frontend): ProductDetailViewModel loads history, correct with refetch"
```

---

## Task 11: designsystem atom — StockLevelBar.trackColor / Thumb / RoundBtn / Stepper / Sheet / Toast

**Files:**
- Modify: `frontend/src/commonMain/.../designsystem/atom/StockLevelBar.kt`
- Create: `frontend/src/commonMain/.../designsystem/atom/Thumb.kt`
- Create: `frontend/src/commonMain/.../designsystem/atom/RoundBtn.kt`
- Create: `frontend/src/commonMain/.../designsystem/atom/Stepper.kt`
- Create: `frontend/src/commonMain/.../designsystem/atom/Sheet.kt`
- Create: `frontend/src/commonMain/.../designsystem/atom/Toast.kt`

UI atom はコンパイル確認のみ（描画網羅テストは追わない）。Material3 を designsystem に封じ込め（feature は import しない）。

- [ ] **Step 0: AppIconName に Back を追加（詳細画面の戻る用）**

`designsystem/atom/AppIcon.kt` の enum と `vector()` に `Back` を追加:

```kotlin
// import 追加
import androidx.compose.material.icons.automirrored.filled.ArrowBack
// enum に Back を追加
enum class AppIconName { Box, Cart, Plus, Minus, Clock, Home, User, Back }
// vector() の when に追加
        AppIconName.Back -> Icons.AutoMirrored.Filled.ArrowBack
```

- [ ] **Step 1: StockLevelBar に trackColor を追加**

```kotlin
@Composable
fun StockLevelBar(
    qty: Int,
    min: Int,
    color: Color,
    modifier: Modifier = Modifier,
    trackColor: Color = color.copy(alpha = 0.16f),
) {
    val comfortable = max(max(min * 2, min + 3), max(qty, 1))
    val pct = (qty.toFloat() / comfortable).coerceIn(0f, 1f)
    LinearProgressIndicator(
        progress = { pct },
        color = color,
        trackColor = trackColor,
        modifier = modifier.fillMaxWidth().height(8.dp),
    )
}
```

- [ ] **Step 2: Thumb を実装（商品サムネ。image は P6-1 では icon フォールバックのみ）**

```kotlin
package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box

/** 商品サムネ。ProductImage 表示は将来（P6-2）。現状は icon プレースホルダ。 */
@Composable
fun Thumb(
    size: Dp = 48.dp,
    radius: Dp = 14.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(RoundedCornerShape(radius))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(AppIconName.Box, contentDescription = null)
    }
}
```

- [ ] **Step 3: RoundBtn を実装**

```kotlin
package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** 円形アイコンボタン（戻る/設定 等の chrome 用）。 */
@Composable
fun RoundBtn(
    icon: AppIconName,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalIconButton(onClick = onClick, shape = CircleShape, modifier = modifier) {
        AppIcon(icon, contentDescription = contentDescription)
    }
}
```

- [ ] **Step 4: Stepper を実装**

```kotlin
package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 数量 ± ステッパ。min 1 でクランプ（補充/消費/訂正の数量入力）。 */
@Composable
fun Stepper(
    value: Int,
    onChange: (Int) -> Unit,
    unit: String,
    modifier: Modifier = Modifier,
    min: Int = 1,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundBtn(AppIconName.Minus, contentDescription = "decrement", onClick = { onChange((value - 1).coerceAtLeast(min)) })
        Text(
            text = "$value$unit",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        RoundBtn(AppIconName.Plus, contentDescription = "increment", onClick = { onChange(value + 1) })
    }
}
```

- [ ] **Step 5: Sheet を実装（ModalBottomSheet ラッパ）**

```kotlin
package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** モーダルボトムシート。open=false の間は何も描かない。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheet(
    open: Boolean,
    title: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!open) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))
            content()
        }
    }
}
```

- [ ] **Step 6: Toast を実装（host）**

```kotlin
package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** 単発トースト表示。message=null の間は何も描かない。 */
@Composable
fun Toast(
    message: String?,
    modifier: Modifier = Modifier,
) {
    if (message == null) return
    Snackbar(modifier = modifier) { Text(message) }
}
```

- [ ] **Step 7: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL

> `ModalBottomSheet`/`rememberModalBottomSheetState` が `@ExperimentalMaterial3Api` で未解決なら、material3 1.10.0-alpha05 の該当 API シグネチャを確認して合わせる（Expressive でも ModalBottomSheet は material3 本体）。

- [ ] **Step 8: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/
git commit -m "feat(frontend): add Thumb/RoundBtn/Stepper/Sheet/Toast atoms, StockLevelBar trackColor"
```

---

## Task 12: ProductCard / MoveSheet（home カードと補充消費シート）

**Files:**
- Create: `frontend/src/commonMain/.../feature/inventory/ui/ProductCard.kt`
- Create: `frontend/src/commonMain/.../feature/inventory/ui/MoveSheet.kt`

mock の `ProductCard`/`CompactCard`/`MoveSheet` 相当。予測・日時ピッカーは除外。

- [ ] **Step 1: ProductCard を実装（list/grid 共用、atom 経由）**

```kotlin
package net.brightroom.mindstock.frontend.feature.inventory.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.action_consume
import mindstock.frontend.generated.resources.action_replenish
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.StockStatus
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.PrimaryButton
import net.brightroom.mindstock.frontend.designsystem.atom.StatusDot
import net.brightroom.mindstock.frontend.designsystem.atom.StockLevelBar
import net.brightroom.mindstock.frontend.designsystem.atom.Thumb
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProductCard(
    stock: Stock,
    onOpen: (Stock) -> Unit,
    onReplenish: (Stock) -> Unit,
    onConsume: (Stock) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMindstockTokens.current
    val statusColor =
        when (stock.status()) {
            StockStatus.在庫切れ -> tokens.statusOut
            StockStatus.残りわずか -> tokens.statusLow
            StockStatus.十分 -> tokens.statusOk
        }
    Column(modifier = modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb()
            Column(modifier = Modifier.fillMaxWidth().padding(end = 8.dp)) {
                AppText(stock.product.name())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(color = statusColor)
                    AppText("${stock.currentQuantity()}${stock.product.setting.unit()}")
                }
            }
        }
        StockLevelBar(qty = stock.currentQuantity(), min = stock.product.setting.minimumStock(), color = statusColor)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(onClick = { onReplenish(stock) }) { AppText(stringResource(Res.string.action_replenish)) }
            PrimaryButton(onClick = { onConsume(stock) }) { AppText(stringResource(Res.string.action_consume)) }
        }
        // タップで詳細（行全体）。簡易にボタンで代替してもよいが onOpen を必ず配線する。
    }
}
```

> `onOpen` は行タップで詳細へ遷移させる配線。Compose の `clickable` を Column に付けるか、商品名を `PrimaryButton`/`TextButton` 化して `onOpen(stock)` を呼ぶ。クイックボタンと競合しないようレイアウトすること。

- [ ] **Step 2: MoveSheet を実装（補充/消費）**

```kotlin
package net.brightroom.mindstock.frontend.feature.inventory.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.action_consume
import mindstock.frontend.generated.resources.action_replenish
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.PrimaryButton
import net.brightroom.mindstock.frontend.designsystem.atom.Sheet
import net.brightroom.mindstock.frontend.designsystem.atom.Stepper
import org.jetbrains.compose.resources.stringResource

enum class MoveMode { Replenish, Consume }

/** 補充/消費シート。数量+メモ（日時ピッカーは無し＝サーバ時刻確定）。 */
@Composable
fun MoveSheet(
    open: Boolean,
    mode: MoveMode,
    stock: Stock?,
    onClose: () -> Unit,
    onSubmit: (quantity: Int, note: String) -> Unit,
) {
    if (stock == null) return
    val isReplenish = mode == MoveMode.Replenish
    val title = stringResource(if (isReplenish) Res.string.action_replenish else Res.string.action_consume)
    var qty by remember(open, stock) { mutableStateOf(1) }
    var note by remember(open, stock) { mutableStateOf("") }
    Sheet(open = open, title = title, onClose = onClose) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            AppText("${stock.product.name()}（現在 ${stock.currentQuantity()}${stock.product.setting.unit()}）")
            Stepper(value = qty, onChange = { qty = it }, unit = stock.product.setting.unit())
            // メモ入力は OutlinedTextField を atom 化していないため、ここでは AppText プレースホルダ。
            // 実装時に designsystem に TextInput atom を足すか、material3 を shell 同様に許容するか plan レビューで確定。
            PrimaryButton(onClick = { onSubmit(qty, note); onClose() }) {
                AppText("$qty${stock.product.setting.unit()} $title")
            }
        }
    }
}
```

> **要決定（実装時）:** メモ/理由のテキスト入力 atom（`TextInput`）が未定義。designsystem に `TextInput` atom を 1 枚足して使う（`frontend-designsystem.md` 準拠）。この atom 追加を MoveSheet/CorrectionSheet 実装の前段に含めること。

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/ProductCard.kt \
        frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/MoveSheet.kt
git commit -m "feat(frontend): ProductCard and MoveSheet (replenish/consume)"
```

---

## Task 13: TextInput atom + ProductDetailScreen / HistoryRow / CorrectionSheet

**Files:**
- Create: `frontend/src/commonMain/.../designsystem/atom/TextInput.kt`
- Create: `frontend/src/commonMain/.../feature/inventory/ui/ProductDetailScreen.kt`
- Modify: `frontend/src/commonMain/.../feature/inventory/ui/MoveSheet.kt`（TextInput でメモ入力を実装）
- Modify: `frontend/src/commonMain/composeResources/values/strings.xml`（履歴/訂正/詳細文言）

- [ ] **Step 1: TextInput atom**

```kotlin
package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        isError = isError,
        modifier = modifier,
        singleLine = true,
    )
}
```

- [ ] **Step 2: strings.xml に詳細/履歴/訂正の文言を追加**

```xml
    <string name="detail_history">履歴</string>
    <string name="detail_history_empty">まだ記録がありません。</string>
    <string name="detail_min_stock">最低在庫 %1$d%2$s</string>
    <string name="history_replenish">補充</string>
    <string name="history_consume">消費</string>
    <string name="history_corrected_badge">訂正済</string>
    <string name="action_correct">訂正</string>
    <string name="correct_title">数量を訂正</string>
    <string name="correct_reason_placeholder">訂正の理由（入力推奨）</string>
    <string name="correct_submit">訂正を記録する</string>
    <string name="move_note_placeholder">メモ（任意・まとめ買い 等）</string>
```

- [ ] **Step 3: MoveSheet のメモ入力を TextInput に差し替え**

`MoveSheet` の Column 内、Stepper と PrimaryButton の間に:

```kotlin
            TextInput(
                value = note,
                onValueChange = { note = it },
                placeholder = stringResource(Res.string.move_note_placeholder),
                modifier = Modifier.fillMaxWidth(),
            )
```

import 追加: `TextInput`, `Res.string.move_note_placeholder`, `fillMaxWidth`。

- [ ] **Step 4: ProductDetailScreen を実装（詳細 + 履歴 + 訂正シート）**

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
import mindstock.frontend.generated.resources.history_consume
import mindstock.frontend.generated.resources.history_corrected_badge
import mindstock.frontend.generated.resources.history_replenish
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementIdentity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.PrimaryButton
import net.brightroom.mindstock.frontend.designsystem.atom.RoundBtn
import net.brightroom.mindstock.frontend.designsystem.atom.Sheet
import net.brightroom.mindstock.frontend.designsystem.atom.Stepper
import net.brightroom.mindstock.frontend.designsystem.atom.TextInput
import net.brightroom.mindstock.frontend.feature.inventory.ProductDetailUiState
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProductDetailScreen(
    stock: Stock,
    detail: ProductDetailUiState,
    onBack: () -> Unit,
    onReplenish: (Stock) -> Unit,
    onConsume: (Stock) -> Unit,
    onCorrect: (target: MovementId, quantity: Int, reason: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var correcting by remember { mutableStateOf<StockMovement?>(null) }
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundBtn(AppIconName.Back, contentDescription = "back", onClick = onBack)
            AppText(stock.product.name())
        }
        AppText("${stock.currentQuantity()}${stock.product.setting.unit()}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(onClick = { onReplenish(stock) }) { AppText(stringResource(Res.string.action_replenish)) }
            PrimaryButton(onClick = { onConsume(stock) }) { AppText(stringResource(Res.string.action_consume)) }
        }
        AppText(stringResource(Res.string.detail_history))
        when (detail) {
            is ProductDetailUiState.Loading -> AppText("…")
            is ProductDetailUiState.Error -> AppText("…")
            is ProductDetailUiState.Content -> {
                if (detail.movements.list.isEmpty()) {
                    AppText(stringResource(Res.string.detail_history_empty))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(detail.movements.list.reversed()) { m -> HistoryRow(m, stock.product.setting.unit(), onCorrect = { correcting = m }) }
                    }
                }
            }
        }
    }

    val target = correcting
    if (target != null) {
        val movementId = (target.identity as? MovementIdentity.Persisted)?.id
        var qty by remember(target) { mutableStateOf(target.quantity()) }
        var reason by remember(target) { mutableStateOf("") }
        Sheet(open = true, title = stringResource(Res.string.correct_title), onClose = { correcting = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Stepper(value = qty, onChange = { qty = it }, unit = stock.product.setting.unit())
                TextInput(value = reason, onValueChange = { reason = it }, placeholder = stringResource(Res.string.correct_reason_placeholder), modifier = Modifier.fillMaxWidth(), isError = reason.isBlank())
                PrimaryButton(
                    onClick = {
                        if (movementId != null && reason.isNotBlank()) {
                            onCorrect(movementId, qty, reason)
                            correcting = null
                        }
                    },
                    enabled = movementId != null && reason.isNotBlank(),
                ) { AppText(stringResource(Res.string.correct_submit)) }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    movement: StockMovement,
    unit: String,
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
        if (movement is StockMovement.Replenishment || movement is StockMovement.Consumption) {
            PrimaryButton(onClick = onCorrect) { AppText(stringResource(Res.string.action_correct)) }
        }
    }
}
```

> 注: `Correction` 行には「訂正」ボタンを出さない（訂正対象は base movement のみ）。`movement.actor.profile.displayName()` で「誰が」。`relTime` 相当の時刻表示は `OccurredAt`（kotlinx-datetime LocalDateTime）から整形する小ヘルパを `feature/inventory` に置いてよい（`history_corrected_badge` の訂正済表示は `StockMovements` の Correction 有無から導出。簡易には省略可、plan レビューで粒度確定）。

- [ ] **Step 5: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/TextInput.kt \
        frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/ProductDetailScreen.kt \
        frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/MoveSheet.kt \
        frontend/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(frontend): ProductDetailScreen with history and correction, TextInput atom"
```

---

## Task 14: StockHomeScreen 作り込み + InventoryRoute（home+detail 束ね）

**Files:**
- Modify: `frontend/src/commonMain/.../feature/inventory/ui/StockHomeScreen.kt`
- Create: `frontend/src/commonMain/.../feature/inventory/ui/InventoryRoute.kt`
- Modify: `frontend/src/commonMain/composeResources/values/strings.xml`（検索/挨拶/追加文言）

StockHomeScreen を検索バー・grid/list・`ProductCard`・追加ボタンへ作り込み。`InventoryRoute` が home/detail/sheet の表示状態を束ね、両 ViewModel を保持する live エントリ。

- [ ] **Step 1: strings.xml に文言追加**

```xml
    <string name="stock_search_placeholder">在庫を検索</string>
    <string name="stock_greeting">こんにちは、%1$s</string>
    <string name="stock_title">在庫</string>
    <string name="stock_add_product">商品を追加</string>
    <string name="stock_search_empty">「%1$s」に一致する在庫はありません</string>
    <string name="need_household">世帯がありません（世帯作成は準備中）</string>
```

- [ ] **Step 2: StockHomeScreen を作り込み**

```kotlin
package net.brightroom.mindstock.frontend.feature.inventory.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.loading
import mindstock.frontend.generated.resources.stock_add_product
import mindstock.frontend.generated.resources.stock_search_placeholder
import mindstock.frontend.generated.resources.stock_view_grid
import mindstock.frontend.generated.resources.stock_view_list
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.frontend.core.ui.resolve
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.PrimaryButton
import net.brightroom.mindstock.frontend.designsystem.atom.SegOption
import net.brightroom.mindstock.frontend.designsystem.atom.SegmentedControl
import net.brightroom.mindstock.frontend.designsystem.atom.TextInput
import net.brightroom.mindstock.frontend.feature.inventory.InventoryUiState
import net.brightroom.mindstock.frontend.feature.inventory.StockView
import org.jetbrains.compose.resources.stringResource

@Composable
fun StockHomeScreen(
    state: InventoryUiState,
    onSelectView: (StockView) -> Unit,
    onQueryChange: (String) -> Unit,
    onOpen: (Stock) -> Unit,
    onReplenish: (Stock) -> Unit,
    onConsume: (Stock) -> Unit,
    onAddProduct: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (state) {
            is InventoryUiState.Loading -> AppText(stringResource(Res.string.loading))
            is InventoryUiState.Error -> AppText(state.text.resolve())
            is InventoryUiState.Content -> {
                TextInput(
                    value = state.query,
                    onValueChange = onQueryChange,
                    placeholder = stringResource(Res.string.stock_search_placeholder),
                    modifier = Modifier.fillMaxWidth(),
                )
                SegmentedControl(
                    options =
                        listOf(
                            SegOption(StockView.List.name, stringResource(Res.string.stock_view_list)),
                            SegOption(StockView.Grid.name, stringResource(Res.string.stock_view_grid)),
                        ),
                    selectedKey = state.view.name,
                    onSelect = { onSelectView(StockView.valueOf(it)) },
                )
                val visible = state.visibleStocks()
                LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(visible.list) { stock ->
                        ProductCard(stock = stock, onOpen = onOpen, onReplenish = onReplenish, onConsume = onConsume)
                    }
                }
                PrimaryButton(onClick = onAddProduct) { AppText(stringResource(Res.string.stock_add_product)) }
            }
        }
    }
}
```

> grid の多列（`CompactCard`/`LazyVerticalGrid`）と desktop の列増は実装時に `state.view == Grid` で `LazyVerticalGrid` に切り替える。本 plan ではまず list を確実に通し、grid 多列は同タスク内の追加実装とする（atom は流用）。

- [ ] **Step 3: InventoryRoute を実装（home+detail+sheet の状態束ね・live エントリ）**

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
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Reason
import net.brightroom.mindstock.frontend.feature.inventory.InventoryViewModel
import net.brightroom.mindstock.frontend.feature.inventory.ProductDetailViewModel
import net.brightroom.mindstock.frontend.feature.inventory.StockView

/**
 * 在庫ホーム + 商品詳細 + 補充/消費/訂正シートの表示状態を束ねる live エントリ。
 * ViewModel の生成は呼び出し側（App）から factory で受ける（householdId 注入・テスト容易性）。
 */
@Composable
fun InventoryRoute(
    homeViewModel: InventoryViewModel,
    detailViewModelFactory: (Stock) -> ProductDetailViewModel,
    onAddProduct: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val state by homeViewModel.state.collectAsState()
    var selected by remember { mutableStateOf<Stock?>(null) }
    var moveTarget by remember { mutableStateOf<Pair<Stock, MoveMode>?>(null) }

    LaunchedEffect(Unit) { homeViewModel.load() }

    val current = selected
    if (current == null) {
        StockHomeScreen(
            state = state,
            onSelectView = { homeViewModel.setView(it) },
            onQueryChange = { homeViewModel.setQuery(it) },
            onOpen = { selected = it },
            onReplenish = { moveTarget = it to MoveMode.Replenish },
            onConsume = { moveTarget = it to MoveMode.Consume },
            onAddProduct = onAddProduct,
            modifier = modifier,
        )
    } else {
        val detailVm = remember(current) { detailViewModelFactory(current) }
        val detailState by detailVm.state.collectAsState()
        LaunchedEffect(current) { detailVm.load() }
        ProductDetailScreen(
            stock = current,
            detail = detailState,
            onBack = { selected = null },
            onReplenish = { moveTarget = it to MoveMode.Replenish },
            onConsume = { moveTarget = it to MoveMode.Consume },
            onCorrect = { target: MovementId, qty: Int, reason: String ->
                scope.launch { detailVm.correct(target, Quantity(qty), Reason(reason)) }
            },
            modifier = modifier,
        )
    }

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
            // 補充/消費後は詳細の Stock が陳腐化する（数量は home の Stocks 由来）。
            // 安全な既定として home に戻し、再フェッチ済みの最新数量を見せる。
            selected = null
        },
    )
}
```

> 注: `Quantity(quantity)` は >0 必須（Stepper の min=1 で担保）。`Reason(reason)` は非空必須（CorrectionSheet の enabled で担保）。詳細から補充/消費した場合は上記のとおり `selected = null` で home に戻す（陳腐化した数量を見せない既定動作）。訂正は history のみの更新なので詳細に留まり `detailVm.load()` で再取得する。

- [ ] **Step 4: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/StockHomeScreen.kt \
        frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/InventoryRoute.kt \
        frontend/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(frontend): StockHome search/cards build-out + InventoryRoute home/detail"
```

---

## Task 15: App / AppShell live 配線（toast host・reauth 受け口・NeedHousehold）

**Files:**
- Modify: `frontend/src/webMain/.../App.kt`
- Modify: `frontend/src/commonMain/.../app/shell/AppShell.kt`

placeholder を実 `InventoryRoute` に差し替え、`ToastController`/`ReauthController` を生成して host と受け口を配線、`NeedHousehold` 状態を表示。

- [ ] **Step 1: AppShell の stockContent を任意 Composable に（既に `stockContent: @Composable () -> Unit`。変更不要なら確認のみ）**

`AppShell` は現状 `stockContent` を受ける。Stock タブにそのまま `InventoryRoute` を渡せる。変更不要。トースト host は App 側で全体オーバーレイにするため AppShell は触らない。

- [ ] **Step 2: App.kt を live 配線**

`App()` を以下の要点で書き換える（既存の http/authClient/session/rpc/vm 構築は維持）:

```kotlin
// 追加 import
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.collectLatest
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.frontend.core.ui.resolve
import net.brightroom.mindstock.frontend.designsystem.atom.Toast
import net.brightroom.mindstock.frontend.feature.inventory.InventoryViewModel
import net.brightroom.mindstock.frontend.feature.inventory.ProductDetailViewModel
import net.brightroom.mindstock.frontend.feature.inventory.data.InventoryRepository
import net.brightroom.mindstock.frontend.feature.inventory.ui.InventoryRoute
import net.brightroom.mindstock.rpc.product.ProductRpcService
import net.brightroom.mindstock.rpc.stock.StockRegisterRpcService
import net.brightroom.mindstock.rpc.stock.StockRpcService
import mindstock.frontend.generated.resources.need_household
```

**まず deps を変数に引き上げる**（reauth 受け口と AuthViewModel の両方で使う）。`App()` 冒頭の `val vm = remember { AuthViewModel(WebAuthDeps(authClient, rpc, session)) }` を次に変更:

```kotlin
    val deps = remember { WebAuthDeps(authClient, rpc, session) }
    val vm = remember { AuthViewModel(deps) }
```

import 追加: `import net.brightroom.mindstock.frontend.auth.TokenStore`。

App body 要点（`MindstockTheme { ... }` 内）:

```kotlin
    val toast = remember { ToastController() }
    val reauth = remember { ReauthController() }
    val sessionState by session.state.collectAsState()
    val toastMessage by toast.current.collectAsState()

    // 再認証受け口（単一）: token 破棄 → WS 閉じ → authorize へ redirect（ページ離脱）
    LaunchedEffect(reauth) {
        reauth.signal.collectLatest {
            TokenStore.clear()
            rpc.close()
            deps.redirectToAuthorize() // suspend。authorize へ遷移しページ離脱
        }
    }

    val repository =
        remember {
            InventoryRepository(
                productService = { rpc.service<ProductRpcService>() },
                stockService = { rpc.service<StockRpcService>() },
                stockRegisterService = { rpc.service<StockRegisterRpcService>() },
            )
        }

    Box(Modifier.fillMaxSize()) {
        when (state) {
            is AuthState.Booting -> AppText(stringResource(Res.string.loading))
            is AuthState.Failed -> AppText((state as AuthState.Failed).message)
            is AuthState.NeedOnboarding -> AppText(stringResource(Res.string.onboarding_placeholder))
            is AuthState.NeedHousehold -> AppText(stringResource(Res.string.need_household))
            is AuthState.Ready -> {
                val householdId = sessionState.activeHouseholdId
                if (householdId == null) {
                    AppText(stringResource(Res.string.need_household))
                } else {
                    val homeVm =
                        remember(householdId) {
                            InventoryViewModel(
                                householdId = householdId,
                                loadStocks = repository::list,
                                replenishStock = repository::replenish,
                                consumeStock = repository::consume,
                                toast = toast,
                                reauth = reauth,
                            )
                        }
                    AppShell(
                        stockContent = {
                            InventoryRoute(
                                homeViewModel = homeVm,
                                detailViewModelFactory = { stock: Stock ->
                                    ProductDetailViewModel(
                                        productId = stock.product.id,
                                        loadHistory = repository::history,
                                        correctMovement = repository::correct,
                                        toast = toast,
                                        reauth = reauth,
                                    )
                                },
                                onAddProduct = { toast.show(/* P6-2 placeholder */ net.brightroom.mindstock.frontend.core.ui.UiText(Res.string.stock_add_product)) },
                            )
                        },
                    )
                }
            }
        }
        // トースト全体オーバーレイ
        Toast(message = toastMessage?.text?.resolve(), modifier = Modifier.align(Alignment.BottomCenter))
        LaunchedEffect(toastMessage) {
            if (toastMessage != null) {
                kotlinx.coroutines.delay(2500)
                toast.dismiss()
            }
        }
    }
```

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 全テスト確認**

Run: `./gradlew :frontend:jsTest`
Expected: PASS（全 ViewModel/controller テスト）

- [ ] **Step 5: Commit**

```bash
git add frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/App.kt \
        frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/shell/AppShell.kt
git commit -m "feat(frontend): wire live StockHome, toast host, reauth receiver, NeedHousehold"
```

---

## Task 16: 仕上げ確認（コンパイル + テスト + live 起動確認）

**Files:** なし（検証のみ）

- [ ] **Step 1: コンパイル + テスト一括**

Run: `./gradlew :frontend:compileKotlinWasmJs :frontend:compileKotlinJs :frontend:jsTest`
Expected: すべて BUILD SUCCESSFUL / PASS

- [ ] **Step 2: spotless/ktlint**

Run: `./gradlew :frontend:spotlessCheck`（または該当タスク）
Expected: PASS（日本語識別子は root `.editorconfig` で許容済み）

- [ ] **Step 3: live 起動確認（手動・任意）**

backend（Zitadel/Postgres）を起動した上で:
Run: `./gradlew :frontend:wasmJsBrowserDevelopmentRun`
確認: ログイン → 在庫一覧が live で表示 → 補充/消費でトースト + 数量更新 → 商品詳細で履歴表示 → 訂正記録。

> live 検証は backend 起動が要るため、Subagent-Driven では最終 holistic 後にユーザ手元で実施する想定。自動テストはここまでで緑。

- [ ] **Step 4: 申し送りメモの更新（メモリ）**

`full-replace-2026-06.md` に P6-1a 完了と P6-1b（買い物/活動タブ・setWanted）残りを追記。

---

## Self-Review（spec 突き合わせ）

- spec §2 live スパイン → Task 5/6/14/15 ✅
- spec §3 書込→再取得/トースト → Task 9/10（再フェッチ）/ Task 3/11/15（toast host）✅
- spec §4 Unauthorized→再認証（単一機構）→ Task 4（signal）/ Task 9/10（request）/ Task 15（受け口）✅
- spec §5.1 StockHome（検索/grid-list/カード/追加）→ Task 12/14 ✅（grid 多列は Task 14 注記で同タスク内）
- spec §5.2 ProductDetail（数量/バー/履歴・wanted は出さない）→ Task 13 ✅
- spec §5.3 MoveSheet → Task 12/13 ✅
- spec §5.4 CorrectionSheet → Task 13 ✅
- spec §6 atom（Sheet/Stepper/Thumb/RoundBtn/Toast + trackColor）→ Task 11（+ TextInput を Task 13 で追加）✅
- spec §7 i18n（UiText/errorText でトースト文言をリソース化）→ Task 1/2 ✅、テスト（ViewModel ロジック）→ Task 5/9/10 ✅

**未確定（実装レビューで詰める・plan に注記済み）:**
1. メモ/理由の `TextInput` atom（Task 12 で必要判明 → Task 13 で追加）。
2. grid 多列 / desktop 列増の具体（Task 14 注記）。
3. 履歴行の時刻整形 / 訂正済バッジの導出粒度（Task 13 注記）。

これらは UI 細部・実装時に確定できる範囲。ロジック（ViewModel/controller/repository/i18n）は完全に TDD で固定済み。
