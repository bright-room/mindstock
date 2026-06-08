# occurredAt バックデート Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 補充 / 消費の `occurredAt` をユーザがバックデート指定できるようにする（モック `screens-b.jsx:MoveSheet` の `DatePick` を再現）。

**Architecture:** ドメインと DB は既に `OccurredAt` 対応済み。本変更は `StockRegisterService` の `OccurredAt.now()` ハードコードを外し、`occurredAt` を RPC → Controller → Service と、frontend では DatePick → MoveSheet → ViewModel → Repository → RPC と通すスレッドスルー。訂正（correct）はモックに日時ピッカーが無いためサーバ `now` 維持。未来日抑止はピッカー UI 側（`SelectableDates`）で行い、サーバ検証は入れない。

**Tech Stack:** Kotlin Multiplatform / kotlinx-rpc / Exposed / Compose Multiplatform(M3 DatePicker) / kotlinx-datetime / Kotest(backend) / kotlin.test(frontend commonTest)

---

## File Structure

**Backend**
- `rpc/.../stock/StockRegisterRpcService.kt`（modify）— `replenish`/`consume` に `occurredAt` 追加
- `backend/api/.../presentation/rpc/stock/StockRegisterController.kt`（modify）— 素通し
- `backend/core/.../application/service/stock/StockRegisterService.kt`（modify）— `now()` 削除・引数化
- `backend/core/.../service/stock/StockRegisterServiceTest.kt`（modify）
- `backend/api/.../presentation/rpc/stock/StockRegisterControllerTest.kt`（modify）

**Frontend**
- `frontend/.../designsystem/atom/AppIcon.kt`（modify）— `Calendar` アイコン追加
- `frontend/.../designsystem/atom/DatePick.kt`（create）— 3チップ + M3 DatePicker ラッパ atom
- `frontend/.../feature/inventory/ui/OccurredAtMath.kt`（create）— 選択日付 → `OccurredAt` の純粋関数
- `frontend/.../feature/inventory/ui/OccurredAtMathTest.kt`（create, commonTest）
- `frontend/.../composeResources/values/strings.xml`（modify）— DatePick 文言
- `frontend/.../feature/inventory/data/InventoryRepository.kt`（modify）
- `frontend/.../feature/inventory/InventoryViewModel.kt`（modify）
- `frontend/.../feature/inventory/ProductDetailViewModel.kt`（modify）
- `frontend/.../feature/inventory/ui/MoveSheet.kt`（modify）— DatePick 行追加・onSubmit 拡張
- `frontend/.../feature/inventory/ui/InventoryRoute.kt`（modify）— call site
- `frontend/.../feature/inventory/ui/ProductDetailOverlay.kt`（modify）— call site
- `frontend/src/webMain/.../App.kt`（modify）— ShoppingListViewModel の replenish 配線
- `frontend/.../feature/inventory/InventoryViewModelTest.kt`（modify）
- `frontend/.../feature/inventory/ProductDetailViewModelTest.kt`（modify）

**Docs**
- `frontend/.../feature/inventory/ui/MoveSheet.kt` KDoc / `StockRegisterRpcService.kt` KDoc（modify）

---

## Task 1: Service が occurredAt を受け取りバックデートする（backend core）

**Files:**
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/stock/StockRegisterService.kt`
- Test: `backend/core/src/test/kotlin/net/brightroom/mindstock/application/service/stock/StockRegisterServiceTest.kt`

- [ ] **Step 1: 既存テストを新シグネチャ + バックデート検証に更新（失敗させる）**

`StockRegisterServiceTest.kt` の replenish テストを次に差し替え、import に `kotlin.time.Duration.Companion.days` 相当は使わず `LocalDateTime` を直接使う。ファイル先頭の import に `net.brightroom.mindstock.extensions.kotlinx.datetime.now`（`:shared`）と `kotlinx.datetime.LocalDateTime` を追加。

`replenish` テストを置換:

```kotlin
test("replenish は渡された occurredAt をそのまま movement に記録する(バックデート)") {
    val backdated = OccurredAt(LocalDateTime(2026, 6, 1, 9, 0))
    val appended = slot<StockMovement>()
    every { productRepository.householdOf(product.id) } returns householdId
    every { householdRepository.findById(householdId) } returns householdWithActor()
    every { residentRepository.findById(actor.id) } returns actor
    every { stockRepository.findByProduct(product.id) } returns Stock(product, StockMovements(emptyList()))
    every { stockRegisterRepository.appendMovement(product.id, capture(appended)) } returns mockk(relaxed = true)

    service.replenish(product.id, Quantity(3), Note(""), backdated, actor.id)

    verify { stockRepository.findByProduct(product.id) }
    check(appended.captured is StockMovement.Replenishment) { "appended movement must be a Replenishment" }
    appended.captured.occurredAt shouldBe backdated
}
```

`consume` テストの呼び出しも `service.consume(product.id, Quantity(2), Note(""), OccurredAt(LocalDateTime(2026, 6, 1, 9, 0)), actor.id)` に更新（引数順は productId, quantity, note, occurredAt, actor）。`MembershipRequiredException` 系テストの `service.replenish(...)` 呼び出しも `OccurredAt.now()` を 4 番目に挿入して更新。`shouldBe` import（`io.kotest.matchers.shouldBe`）が無ければ追加。

- [ ] **Step 2: テスト実行して失敗確認**

Run: `./gradlew :backend:core:test --tests "*StockRegisterServiceTest*"`
Expected: コンパイルエラー（`replenish` が 4 引数を受け付けない）

- [ ] **Step 3: Service を occurredAt 引数化**

`StockRegisterService.kt` の `replenish` / `consume` を変更（`correct` は変更しない）:

```kotlin
fun replenish(
    productId: ProductId,
    quantity: Quantity,
    note: Note,
    occurredAt: OccurredAt,
    actor: ResidentId,
) {
    authorizeProduct(productId, actor)
    val resident = residentRepository.findById(actor)
    val stock = stockRepository.findByProduct(productId)
    val replenished = stock.replenish(quantity, occurredAt, resident, note)
    stockRegisterRepository.appendMovement(productId, replenished.latestMovement())
}

fun consume(
    productId: ProductId,
    quantity: Quantity,
    note: Note,
    occurredAt: OccurredAt,
    actor: ResidentId,
) {
    authorizeProduct(productId, actor)
    val resident = residentRepository.findById(actor)
    val stock = stockRepository.findByProduct(productId)
    val consumed = stock.consume(quantity, occurredAt, resident, note)
    stockRegisterRepository.appendMovement(productId, consumed.latestMovement())
}
```

- [ ] **Step 4: テスト実行して成功確認**

Run: `./gradlew :backend:core:test --tests "*StockRegisterServiceTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/stock/StockRegisterService.kt backend/core/src/test/kotlin/net/brightroom/mindstock/application/service/stock/StockRegisterServiceTest.kt
git commit -m "feat(backend): StockRegisterService の補充/消費を occurredAt 引数化(バックデート)"
```

---

## Task 2: RPC interface + Controller が occurredAt を通す（backend rpc/api）

**Files:**
- Modify: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/stock/StockRegisterRpcService.kt`
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/stock/StockRegisterController.kt`
- Test: `backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/rpc/stock/StockRegisterControllerTest.kt`

- [ ] **Step 1: ControllerTest を新シグネチャに更新（失敗させる）**

`StockRegisterControllerTest.kt` の replenish/consume テストを更新。先頭 import に `net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt`・`kotlinx.datetime.LocalDateTime` を追加し、replenish テストを置換:

```kotlin
test("replenish は StockRegisterService.replenish を occurredAt 付きで呼び Ok(Unit) を返す") {
    val occurredAt = OccurredAt(LocalDateTime(2026, 6, 1, 9, 0))
    controller.replenish(productId, Quantity(3), Note("補充"), occurredAt) shouldBe RpcResult.Ok(Unit)
    verify { stockRegisterService.replenish(productId, Quantity(3), Note("補充"), occurredAt, residentId) }
}
```

consume テストも同様に occurredAt を 4 番目の引数として追加し、`verify` の Service 呼び出しも `(productId, quantity, note, occurredAt, residentId)` に更新する。

- [ ] **Step 2: テスト実行して失敗確認**

Run: `./gradlew :backend:api:test --tests "*StockRegisterControllerTest*"`
Expected: コンパイルエラー（`replenish` が 4 引数を受け付けない）

- [ ] **Step 3: RPC interface に occurredAt を追加**

`StockRegisterRpcService.kt` の `replenish`/`consume` を変更し、import に `net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt` を追加。KDoc も更新:

```kotlin
/** 在庫を補充する(UC14)。Stock は productId で特定。actor は session 由来、occurredAt はクライアント指定(バックデート可)。 */
suspend fun replenish(
    productId: ProductId,
    quantity: Quantity,
    note: Note,
    occurredAt: OccurredAt,
): RpcResult<Unit, RpcError>

/** 在庫を消費する(UC15)。occurredAt はクライアント指定(バックデート可)。 */
suspend fun consume(
    productId: ProductId,
    quantity: Quantity,
    note: Note,
    occurredAt: OccurredAt,
): RpcResult<Unit, RpcError>
```

- [ ] **Step 4: Controller で occurredAt を素通し**

`StockRegisterController.kt` の `replenish`/`consume` を変更（import に `OccurredAt` 追加）:

```kotlin
override suspend fun replenish(
    productId: ProductId,
    quantity: Quantity,
    note: Note,
    occurredAt: OccurredAt,
): RpcResult<Unit, RpcError> =
    requireRegistered(session) { residentId ->
        stockRegisterService.replenish(productId, quantity, note, occurredAt, residentId)
        RpcResult.Ok(Unit)
    }

override suspend fun consume(
    productId: ProductId,
    quantity: Quantity,
    note: Note,
    occurredAt: OccurredAt,
): RpcResult<Unit, RpcError> =
    requireRegistered(session) { residentId ->
        stockRegisterService.consume(productId, quantity, note, occurredAt, residentId)
        RpcResult.Ok(Unit)
    }
```

- [ ] **Step 5: テスト実行して成功確認**

Run: `./gradlew :backend:api:test --tests "*StockRegisterControllerTest*"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/stock/StockRegisterRpcService.kt backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/stock/StockRegisterController.kt backend/api/src/test/kotlin/net/brightroom/mindstock/presentation/rpc/stock/StockRegisterControllerTest.kt
git commit -m "feat(backend): replenish/consume RPC に occurredAt を追加"
```

---

## Task 3: DatePick atom + occurredAt 計算ヘルパー（frontend designsystem）

**Files:**
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/AppIcon.kt`
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/OccurredAtMath.kt`
- Create: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/OccurredAtMathTest.kt`
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/DatePick.kt`
- Modify: `frontend/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: occurredAt 計算ヘルパーのテストを書く（失敗させる）**

`OccurredAtMath.kt` は「選択された `LocalDate` と現在の `LocalDateTime`（時刻部）から `OccurredAt` を組む」純粋関数。テスト `OccurredAtMathTest.kt`:

```kotlin
package net.brightroom.mindstock.frontend.feature.inventory.ui

import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test

class OccurredAtMathTest {
    @Test
    fun combines_selected_date_with_current_time() {
        val now = LocalDateTime(2026, 6, 8, 14, 30, 15)
        val picked = LocalDate(2026, 6, 6)
        occurredAtOf(picked, now)() shouldBe LocalDateTime(2026, 6, 6, 14, 30, 15)
    }

    @Test
    fun today_keeps_full_now() {
        val now = LocalDateTime(2026, 6, 8, 14, 30, 15)
        occurredAtOf(LocalDate(2026, 6, 8), now)() shouldBe now
    }
}
```

- [ ] **Step 2: テスト実行して失敗確認**

Run: `./gradlew :frontend:compileTestKotlinJs` （または `:frontend:jsTest`）
Expected: コンパイルエラー（`occurredAtOf` 未定義）

- [ ] **Step 3: ヘルパーを実装**

`OccurredAtMath.kt`:

```kotlin
package net.brightroom.mindstock.frontend.feature.inventory.ui

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt

/** 選択された日付に現在時刻(時・分・秒)を合わせて OccurredAt を作る。モックの「今日=now / 昨日=now-1d」を一般化したもの。 */
fun occurredAtOf(
    date: LocalDate,
    now: LocalDateTime,
): OccurredAt = OccurredAt(LocalDateTime(date, now.time))
```

- [ ] **Step 4: テスト実行して成功確認**

Run: `./gradlew :frontend:jsTest --tests "*OccurredAtMathTest*"`
Expected: PASS

- [ ] **Step 5: Calendar アイコンを追加**

`AppIcon.kt` の `enum class AppIconName` に `Calendar,` を追加（`ListView,` の後）。`vector()` の `when` に分岐追加。ファイル先頭の material-icons import 群に `androidx.compose.material.icons.outlined.CalendarMonth` を追加:

```kotlin
AppIconName.Calendar -> Icons.Outlined.CalendarMonth
```

- [ ] **Step 6: DatePick 文言を strings.xml に追加**

`strings.xml` に追加（`move_*` 群の近く）:

```xml
<string name="move_when_label">いつの出来事？</string>
<string name="date_today">今日</string>
<string name="date_yesterday">昨日</string>
<string name="date_day_before">おととい</string>
```

- [ ] **Step 7: DatePick atom を実装**

`DatePick.kt`。3 チップ（今日/昨日/おととい）+ カレンダーボタン → M3 `DatePickerDialog`。選択は `LocalDate`、未来日は `SelectableDates` で不可。モック実数値: チップ height 42 / radius 12 / `600 13.5px` / active=accent ボーダー+accentSoft 背景・非active=line ボーダー+surface 背景・sub 文字色。カレンダーボタン 46×42 / radius 12。

```kotlin
package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.date_day_before
import mindstock.frontend.generated.resources.date_today
import mindstock.frontend.generated.resources.date_yesterday
import mindstock.frontend.generated.resources.move_when_label
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePick(
    today: LocalDate,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMindstockTokens.current
    var dialogOpen by remember { mutableStateOf(false) }
    val chips =
        listOf(
            stringResource(Res.string.date_today) to today,
            stringResource(Res.string.date_yesterday) to today.minus(DatePeriod(days = 1)),
            stringResource(Res.string.date_day_before) to today.minus(DatePeriod(days = 2)),
        )
    Column(modifier = modifier) {
        AppText(
            stringResource(Res.string.move_when_label),
            style = MindstockTypeDateLabel,
            color = tokens.faint,
        )
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            chips.forEach { (label, date) ->
                val active = date == selected
                OutlinedButton(
                    onClick = { onSelect(date) },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (active) tokens.accent else tokens.line),
                    modifier = Modifier.weight(1f).height(42.dp),
                ) {
                    AppText(label, style = MindstockTypeChip, color = if (active) tokens.accent else tokens.sub)
                }
            }
            OutlinedButton(
                onClick = { dialogOpen = true },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, tokens.line),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                modifier = Modifier.width(46.dp).height(42.dp),
            ) {
                AppIcon(AppIconName.Calendar, contentDescription = null, tint = tokens.sub, size = 19.dp)
            }
        }
    }

    if (dialogOpen) {
        val state =
            rememberDatePickerState(
                selectableDates =
                    object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                            val d = Instant.fromEpochMilliseconds(utcTimeMillis).toLocalDateTime(TimeZone.UTC).date
                            return d <= today
                        }
                    },
            )
        DatePickerDialog(
            onDismissRequest = { dialogOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onSelect(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date)
                    }
                    dialogOpen = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { dialogOpen = false }) { Text("キャンセル") } },
        ) {
            DatePicker(state = state)
        }
    }
}

private val MindstockTypeDateLabel =
    androidx.compose.ui.text.TextStyle(fontSize = 12.5f.sp, fontWeight = FontWeight.SemiBold, color = Color.Unspecified)
private val MindstockTypeChip =
    androidx.compose.ui.text.TextStyle(fontSize = 13.5f.sp, fontWeight = FontWeight.SemiBold, color = Color.Unspecified)
```

> 注: `OutlinedButton` の背景 active 色（accentSoft）は `colors = ButtonDefaults.outlinedButtonColors(containerColor = ...)` で指定。render-verify（Task 6）でモックと突き合わせて微調整する。OK/キャンセルの文言は本タスクでは literal 仮置きし、Task 6 で `strings.xml` に移す（既存 atom の慣行に合わせる）。

- [ ] **Step 8: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinJs`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/AppIcon.kt frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/DatePick.kt frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/OccurredAtMath.kt frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/OccurredAtMathTest.kt frontend/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(frontend): DatePick atom と occurredAt 計算ヘルパーを追加"
```

---

## Task 4: occurredAt を frontend に通す（data → VM → UI → App）

このタスクは Kotlin の型カスケードのため一括で行う（途中コミットでは frontend がコンパイルしない）。

**Files:**
- Modify: `InventoryRepository.kt` / `InventoryViewModel.kt` / `ProductDetailViewModel.kt` / `MoveSheet.kt` / `InventoryRoute.kt` / `ProductDetailOverlay.kt` / `App.kt`
- Test: `InventoryViewModelTest.kt` / `ProductDetailViewModelTest.kt`

- [ ] **Step 1: VM テストを新シグネチャに更新（失敗させる）**

`InventoryViewModelTest.kt` の `vm(...)` ヘルパーの replenish/consume ラムダ型に `OccurredAt` を追加し、呼び出しを更新。import に `OccurredAt`・`kotlinx.datetime.LocalDateTime` を追加:

```kotlin
replenish: suspend (ProductId, Quantity, Note, OccurredAt) -> RpcOutcome<Unit> = { _, _, _, _ -> RpcOutcome.Success(Unit) },
consume: suspend (ProductId, Quantity, Note, OccurredAt) -> RpcOutcome<Unit> = { _, _, _, _ -> RpcOutcome.Success(Unit) },
```

`replenish_success_refetches_and_toasts` 等で `v.replenish(...)` を呼ぶ箇所に `OccurredAt(LocalDateTime(2026, 6, 8, 9, 0))` を末尾引数として追加。

`ProductDetailViewModelTest.kt` も同様に replenish/consume ラムダ型へ `OccurredAt` を追加し、`vm.replenish(quantity, note)` 呼び出しに occurredAt を追加。

- [ ] **Step 2: テスト実行して失敗確認**

Run: `./gradlew :frontend:jsTest --tests "*InventoryViewModelTest*"`
Expected: コンパイルエラー

- [ ] **Step 3: Repository を occurredAt 引数化**

`InventoryRepository.kt`（import に `OccurredAt` 追加）:

```kotlin
suspend fun replenish(
    productId: ProductId,
    quantity: Quantity,
    note: Note,
    occurredAt: OccurredAt,
): RpcOutcome<Unit> = stockRegisterService().replenish(productId, quantity, note, occurredAt).toOutcome()

suspend fun consume(
    productId: ProductId,
    quantity: Quantity,
    note: Note,
    occurredAt: OccurredAt,
): RpcOutcome<Unit> = stockRegisterService().consume(productId, quantity, note, occurredAt).toOutcome()
```

- [ ] **Step 4: InventoryViewModel を occurredAt 引数化**

`InventoryViewModel.kt`（import に `OccurredAt` 追加）。injected ラムダ型と public メソッドを更新:

```kotlin
private val replenishStock: suspend (ProductId, Quantity, Note, OccurredAt) -> RpcOutcome<Unit>,
private val consumeStock: suspend (ProductId, Quantity, Note, OccurredAt) -> RpcOutcome<Unit>,
```

```kotlin
suspend fun replenish(
    productId: ProductId,
    quantity: Quantity,
    note: Note,
    occurredAt: OccurredAt,
) = write(replenishStock(productId, quantity, note, occurredAt), UiText(Res.string.toast_replenished))

suspend fun consume(
    productId: ProductId,
    quantity: Quantity,
    note: Note,
    occurredAt: OccurredAt,
) = write(consumeStock(productId, quantity, note, occurredAt), UiText(Res.string.toast_consumed))
```

- [ ] **Step 5: ProductDetailViewModel を occurredAt 引数化**

`ProductDetailViewModel.kt`（import に `OccurredAt` 追加）。injected ラムダ型 `replenishStock`/`consumeStock` に `OccurredAt` を追加し、public メソッドを更新:

```kotlin
suspend fun replenish(
    quantity: Quantity,
    note: Note,
    occurredAt: OccurredAt,
) = write(replenishStock(productId, quantity, note, occurredAt), UiText(Res.string.toast_replenished))

suspend fun consume(
    quantity: Quantity,
    note: Note,
    occurredAt: OccurredAt,
) = write(consumeStock(productId, quantity, note, occurredAt), UiText(Res.string.toast_consumed))
```

- [ ] **Step 6: MoveSheet に DatePick を追加し onSubmit を拡張**

`MoveSheet.kt`:
- import 追加: `net.brightroom.mindstock.frontend.designsystem.atom.DatePick`, `net.brightroom.mindstock.frontend.feature.inventory.ui.occurredAtOf`, `net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt`, `net.brightroom.mindstock.extensions.kotlinx.datetime.now`, `kotlinx.datetime.LocalDateTime`, `kotlinx.datetime.LocalDate`
- KDoc を「日時ピッカーで occurredAt を指定（バックデート可）」に更新
- `onSubmit` 型を `(quantity: Int, note: String, occurredAt: OccurredAt) -> Unit` に変更
- 状態追加と DatePick 行（メモ欄 `TextInput` の直前に挿入）:

```kotlin
val today = remember(open) { LocalDateTime.now().date }
var pickedDate by remember(open, stock) { mutableStateOf(today) }
```

```kotlin
DatePick(
    today = today,
    selected = pickedDate,
    onSelect = { pickedDate = it },
    modifier = Modifier.fillMaxWidth(),
)
```

- submit を更新:

```kotlin
onClick = {
    onSubmit(qty, note, occurredAtOf(pickedDate, LocalDateTime.now()))
    onClose()
},
```

- [ ] **Step 7: 呼び出し側 2 箇所を更新**

`InventoryRoute.kt` の `onSubmit`:

```kotlin
onSubmit = { quantity, note, occurredAt ->
    val stock = /* 既存の対象 stock 取得ロジックをそのまま使う */ moveTarget ?: return@MoveSheet
    scope.launch {
        when (moveMode) {
            MoveMode.Replenish -> homeViewModel.replenish(stock.product.id, Quantity(quantity), Note(note), occurredAt)
            MoveMode.Consume -> homeViewModel.consume(stock.product.id, Quantity(quantity), Note(note), occurredAt)
        }
    }
},
```
> 既存ラムダの本体（stock 解決・scope.launch・when 分岐）は現行のままで、引数に `occurredAt` を足し、`replenish`/`consume` 呼び出しの末尾に `occurredAt` を渡すだけ。現行の変数名（`moveTarget`/`moveMode`/`scope` 等）は既存コードに合わせる。

`ProductDetailOverlay.kt` の `onSubmit`:

```kotlin
onSubmit = { quantity, note, occurredAt ->
    scope.launch {
        when (moveMode) {
            MoveMode.Replenish -> vm.replenish(Quantity(quantity), Note(note), occurredAt)
            MoveMode.Consume -> vm.consume(Quantity(quantity), Note(note), occurredAt)
        }
    }
},
```

- [ ] **Step 8: App.kt の ShoppingListViewModel 配線を更新**

`App.kt`。`InventoryViewModel` / `ProductDetailViewModel` の `replenishStock = repository::replenish` は 4 引数化された Repository メソッドと自動整合するため変更不要。`ShoppingListViewModel`（replenish に日時ピッカー無し=now）はラムダ型が 3 引数のままなので、メソッド参照をラムダに変更:

import に `net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt` を追加。`ShoppingListViewModel(...)` の `replenishStock = repository::replenish` を:

```kotlin
replenishStock = { pid, q, n -> repository.replenish(pid, q, n, OccurredAt.now()) },
```

- [ ] **Step 9: テスト + コンパイル確認**

Run: `./gradlew :frontend:jsTest`
Expected: PASS（VM テスト含む）

Run: `./gradlew :frontend:compileKotlinJs`
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: Commit**

```bash
git add frontend/src
git commit -m "feat(frontend): occurredAt を MoveSheet→VM→Repository に配線(補充/消費)"
```

---

## Task 5: ドキュメント / KDoc の「サーバ確定」記述を覆す

**Files:**
- Modify: 該当 KDoc / spec（grep で特定）

- [ ] **Step 1: 残存する「サーバ時刻確定」記述を検出**

Run: `grep -rn "サーバ時刻確定\|サーバ時刻由来\|日時ピッカーは無し\|occurredAt.*サーバ" frontend/src docs/superpowers/specs .claude/rules`
Expected: `MoveSheet.kt` KDoc 等がヒット（Task 2/4 で更新済みなら差分なし）

- [ ] **Step 2: 残りを更新**

ヒットした箇所を「occurredAt はクライアント指定（バックデート可）。訂正のみサーバ now」に書き換える。spec に該当文があれば同様に修正（履歴的記述は「P6-4b で覆した」と注記）。

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "docs: occurredAt サーバ確定 の記述をバックデート可へ更新"
```

---

## Task 6: render-verify（DatePick の忠実度確認）

**Files:** なし（手動検証 + 微調整）

- [ ] **Step 1: dev server を起動**

Run: `./gradlew :frontend:jsBrowserDevelopmentRun --continuous`（バックグラウンド）

- [ ] **Step 2: MoveSheet を描画してモックと突合**

`?preview=` harness（[[fidelity-verify-loop-mechanics]]）で MoveSheet を表示し、モック `screens-b.jsx:DatePick` と同寸 side-by-side で突合: チップの height42/radius12/`600 13.5px`/active 配色（accent ボーダー + accentSoft 背景）・カレンダーボタン 46×42・ラベル `600 12.5px` faint。差分があれば `DatePick.kt` の色/サイズを調整。

- [ ] **Step 3: OK/キャンセル文言を strings.xml へ**

`DatePickerDialog` の `"OK"`/`"キャンセル"` literal を `strings.xml`（`date_picker_ok`/`date_picker_cancel`）へ移し、`stringResource` 参照に変更（i18n ルール遵守）。

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "fix(frontend): DatePick をモック実描画で忠実化・文言を resource 化"
```

---

## Self-Review

- **Spec coverage:** RPC(Task2)/Controller(Task2)/Service(Task1)/Domain(無変更=Task1で確認)/Frontend DatePick(Task3,4,6)/occurredAt 構築(Task3)/correct は now 維持(Task1で touch せず)/未来日抑止 UI(Task3 SelectableDates)/doc 反転(Task5)/テスト(Task1,2,4) — すべて対応。
- **Placeholder:** Task4 Step7 の「既存ロジックそのまま」は現行ファイルに依存する箇所のため、変数名を既存に合わせる旨を明記済み（実装者は現行 `InventoryRoute.kt` を読む）。
- **Type consistency:** `occurredAtOf(date, now): OccurredAt` / `replenish(..., occurredAt: OccurredAt, actor)`（backend は actor 末尾、frontend は occurredAt 末尾）— レイヤで引数順が違う点を各タスクで明示済み。`OccurredAt` のコンストラクタは public（value class primary）で frontend からも構築可。

---

## 検証（全体）

- `./gradlew :backend:core:test :backend:api:test`
- `./gradlew :frontend:jsTest :frontend:compileKotlinJs`
- dev server で MoveSheet の DatePick をモックと render 突合
