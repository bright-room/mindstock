# 在庫アラート通知(お知らせ / NotifSheet)実装プラン

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development(推奨)または superpowers:executing-plans でタスク単位に実装する。ステップは `- [ ]` で追跡。frontend のみ・backend 変更なし。`stockAlerts` 純関数のみ TDD 対象、UI/配線は描画網羅を追わず render-verify で確認(`frontend-compose-conventions` / `fidelity-verify-loop-mechanics`)。

**Goal:** 在庫ホーム等のベル🔔をタップすると「お知らせ」シート(`NotifSheet`)が開き、在庫切れ/残りわずか/もうすぐ切れそうな商品の一覧(先頭6件)が表示され、行タップで商品詳細へ遷移する。バッジはアラートの有無で点灯する。

**Architecture:** backend 変更ゼロ。既にロード済みの `Stocks`(`InventoryViewModel` 保持)から純関数 `stockAlerts()` でアラートを導出し、`feature/notification/` の `NotifSheet` に渡す。ベル配線は `App.kt`(ReadyContent)→ `AppShell`(デスクトップ)/ `InventoryRoute`→`StockHomeScreen`(モバイル)。

**Tech Stack:** Kotlin Multiplatform(commonMain)/ Compose Multiplatform / 既存 `:domain`(`Stock.status` / `Stock.forecast`)/ Compose Resources(i18n)。

---

## File Structure

| ファイル | 役割 | 操作 |
|---|---|---|
| `frontend/.../feature/notification/StockAlert.kt` | アラートモデル(`StockAlert` + `AlertReason`) | Create |
| `frontend/.../feature/notification/StockAlerts.kt` | 純関数 `stockAlerts(stocks, asOf)` | Create |
| `frontend/.../feature/notification/ui/NotifSheet.kt` | お知らせシート Composable | Create |
| `frontend/src/commonTest/.../feature/notification/StockAlertsTest.kt` | 純関数テスト | Create |
| `frontend/src/commonMain/composeResources/values/strings.xml` | i18n 5件追加 | Modify |
| `frontend/.../feature/inventory/ui/StockHomeScreen.kt` | ベル onClick/badge を引数化 | Modify |
| `frontend/.../feature/inventory/ui/InventoryRoute.kt` | `onBell`/`hasAlerts` を素通し | Modify |
| `frontend/src/webMain/.../App.kt` | アラート導出・notifOpen・NotifSheet 配置・ベル配線 | Modify |

パッケージ root: `net.brightroom.mindstock.frontend`。commonMain の物理 path は `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/`。

---

## Task 1: StockAlert モデルと stockAlerts 純関数(TDD)

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/notification/StockAlert.kt`
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/notification/StockAlerts.kt`
- Test: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/notification/StockAlertsTest.kt`

- [ ] **Step 1: モデルを書く(`StockAlert.kt`)**

```kotlin
package net.brightroom.mindstock.frontend.feature.notification

import net.brightroom.mindstock.domain.model.inventory.stock.Stock

/** ベル(お知らせ)に並ぶ在庫アラート 1 件。client 派生のビュー型。 */
data class StockAlert(
    val stock: Stock,
    val reason: AlertReason,
)

/** アラートの理由。mock NotifSheet のメッセージ分岐に対応。 */
sealed interface AlertReason {
    /** 在庫を切らしています(status=在庫切れ)。 */
    data object OutOfStock : AlertReason

    /** そろそろ補充どきです(status=残りわずか)。 */
    data object RunningLow : AlertReason

    /** あと約 days 日で切れそうです(status=十分 かつ 予測 <= 5 日)。 */
    data class RunningOutSoon(val days: Int) : AlertReason
}
```

- [ ] **Step 2: 失敗するテストを書く(`StockAlertsTest.kt`)**

テスト用 `Stock` の組み立ては既存テストのヘルパを踏襲する。まず `frontend/src/commonTest` 配下と `domain/src/commonTest` で `Stock(` を生成しているテストを確認し、同じ手で組む。

Run(まずヘルパ調査): `grep -rn "Stock(" frontend/src/commonTest domain/src/commonTest | head`

調査後、以下の振る舞いを検証する(`stock(...)` は調査で判明した生成手段に置き換える。最低在庫・現在数量・消費履歴で status/forecast を制御する):

```kotlin
package net.brightroom.mindstock.frontend.feature.notification

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import net.brightroom.mindstock.domain.model.inventory.stock.EvaluatedTime
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import kotlin.test.Test

class StockAlertsTest {
    private val asOf = EvaluatedTime.now()

    @Test
    fun 在庫切れは_OutOfStock() {
        val alerts = stockAlerts(Stocks(listOf(outOfStockStock())), asOf)
        alerts shouldHaveSize 1
        alerts.first().reason shouldBe AlertReason.OutOfStock
    }

    @Test
    fun 残りわずかは_RunningLow() {
        val alerts = stockAlerts(Stocks(listOf(runningLowStock())), asOf)
        alerts.first().reason shouldBe AlertReason.RunningLow
    }

    @Test
    fun 十分かつ予測5日以内は_RunningOutSoon() {
        val alerts = stockAlerts(Stocks(listOf(soonStock())), asOf)
        alerts.first().reason.shouldBeInstanceOf<AlertReason.RunningOutSoon>()
    }

    @Test
    fun 十分かつ予測なしや6日以上は除外() {
        val alerts = stockAlerts(Stocks(listOf(healthyStock())), asOf)
        alerts shouldHaveSize 0
    }

    @Test
    fun 先頭6件に切り詰める() {
        val alerts = stockAlerts(Stocks(List(8) { outOfStockStock() }), asOf)
        alerts shouldHaveSize 6
    }

    @Test
    fun 空在庫は空リスト() {
        stockAlerts(Stocks(emptyList()), asOf) shouldHaveSize 0
    }

    // 以下 6 つは Step 2 調査で判明した Stock 生成手段で実装する。
    // outOfStockStock(): currentQuantity <= 0
    // runningLowStock():  0 < currentQuantity <= minimumStock
    // soonStock():        currentQuantity > minimumStock かつ 消費履歴ありで forecast <= 5 日
    // healthyStock():     currentQuantity > minimumStock かつ forecast 無し(消費履歴なし)
    private fun outOfStockStock(): net.brightroom.mindstock.domain.model.inventory.stock.Stock = TODO("Step 2 調査で実装")
    private fun runningLowStock(): net.brightroom.mindstock.domain.model.inventory.stock.Stock = TODO("Step 2 調査で実装")
    private fun soonStock(): net.brightroom.mindstock.domain.model.inventory.stock.Stock = TODO("Step 2 調査で実装")
    private fun healthyStock(): net.brightroom.mindstock.domain.model.inventory.stock.Stock = TODO("Step 2 調査で実装")
}
```

> 注: 上記 `TODO("...")` は **テストヘルパの実装待ち**であり、本体コードのプレースホルダではない。Step 2 の `grep` 結果に従い、既存の `Stock` 生成パターン(コンストラクタ or テストファクトリ)で 4 つのヘルパを埋めてからコミットする。`soonStock`/`healthyStock` の forecast 制御方法は `Stock.forecast` の実装(`domain/.../Stock.kt:101` 付近)と既存 forecast テストを読んで合わせる。

- [ ] **Step 3: テストが落ちることを確認**

Run: `./gradlew :frontend:compileTestKotlinJs` → `stockAlerts` 未定義でコンパイルエラー(= FAIL)。

- [ ] **Step 4: 本体を書く(`StockAlerts.kt`)**

```kotlin
package net.brightroom.mindstock.frontend.feature.notification

import net.brightroom.mindstock.domain.model.inventory.stock.ConsumptionForecast
import net.brightroom.mindstock.domain.model.inventory.stock.EvaluatedTime
import net.brightroom.mindstock.domain.model.inventory.stock.StockStatus
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks

/** 上限。mock NotifSheet の .slice(0, 6) に対応。 */
private const val MAX_ALERTS = 6

/** 予測日数の閾値。mock の d <= 5 に対応。 */
private const val SOON_DAYS = 5

/**
 * 在庫からお知らせ用アラートを導出する。mock `app/screens-c.jsx` NotifSheet と同条件。
 * status 優先(在庫切れ/残りわずかは forecast によらず status 理由)。十分なら forecast<=5 のみ拾う。
 * forecast が DaysRemaining を返す時点で在庫>0 が保証される(mock の qty>0 ガードは自動充足)。
 */
fun stockAlerts(stocks: Stocks, asOf: EvaluatedTime): List<StockAlert> =
    stocks.list.mapNotNull { stock ->
        when (stock.status()) {
            StockStatus.在庫切れ -> StockAlert(stock, AlertReason.OutOfStock)
            StockStatus.残りわずか -> StockAlert(stock, AlertReason.RunningLow)
            StockStatus.十分 ->
                when (val forecast = stock.forecast(asOf)) {
                    is ConsumptionForecast.DaysRemaining ->
                        if (forecast() <= SOON_DAYS) StockAlert(stock, AlertReason.RunningOutSoon(forecast())) else null
                    ConsumptionForecast.Unknown -> null
                }
        }
    }.take(MAX_ALERTS)
```

- [ ] **Step 5: テストが通ることを確認**

Run: `./gradlew :frontend:compileTestKotlinJs && ./gradlew :frontend:jsTest --tests "*StockAlertsTest*"`
Expected: PASS(全 6 テスト)。

> KMP commonTest は Kotest FunSpec 不可・`kotlin.test.@Test` + Kotest assertions(`frontend-kmp-test-style`)。`jsTest` で十分(wasmJs はフルビルドが OOM しやすい・`local-build-tips`)。

- [ ] **Step 6: コミット**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/notification/StockAlert.kt \
        frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/notification/StockAlerts.kt \
        frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/notification/StockAlertsTest.kt
git commit -m "feat(notification): 在庫アラート導出 stockAlerts と StockAlert モデルを追加"
```

---

## Task 2: i18n 文言を追加

**Files:**
- Modify: `frontend/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: 文言 5 件を追加**

既存の `forecast_days_left`(317 行付近)の近くに追記する。`%1$d` はそのまま(エスケープ不要、既存 `forecast_days_left` と同形式)。

```xml
<string name="notif_title">お知らせ</string>
<string name="notif_subtitle">在庫減少のお知らせ（将来は Web Push で端末に通知）</string>
<string name="notif_alert_out">在庫を切らしています</string>
<string name="notif_alert_low">そろそろ補充どきです</string>
<string name="notif_alert_soon">あと約%1$d日で切れそうです</string>
```

- [ ] **Step 2: リソースが生成されることを確認**

Run: `./gradlew :frontend:generateComposeResClass`
Expected: BUILD SUCCESSFUL(`Res.string.notif_title` 等が生成される)。

- [ ] **Step 3: コミット**

```bash
git add frontend/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(notification): お知らせ文言を strings.xml に追加"
```

---

## Task 3: NotifSheet Composable

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/notification/ui/NotifSheet.kt`

- [ ] **Step 1: NotifSheet を書く**

`Sheet` atom(`open`/`title`/`onClose`/`content`)の中に副文 + アラート行リストを置く。行は `designsystem/atom`(`AppIcon` / `AppText`)と `LocalMindstockTokens` で組む(`frontend-designsystem`: feature は Material3 を直接使わない)。型寸法は mock 値(名前 600/14、メッセージ 500/12)に preset + `.copy` で寄せ、最終調整は Task 6 の render-verify で行う。

```kotlin
package net.brightroom.mindstock.frontend.feature.notification.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.notif_alert_low
import mindstock.frontend.generated.resources.notif_alert_out
import mindstock.frontend.generated.resources.notif_alert_soon
import mindstock.frontend.generated.resources.notif_subtitle
import mindstock.frontend.generated.resources.notif_title
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.Sheet
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import net.brightroom.mindstock.frontend.feature.notification.AlertReason
import net.brightroom.mindstock.frontend.feature.notification.StockAlert
import org.jetbrains.compose.resources.stringResource

/**
 * お知らせ(在庫アラート一覧)シート。mock app/screens-c.jsx NotifSheet 準拠。
 * client 派生(サーバ通知なし)。行タップで onOpen(stock) → 商品詳細へ。
 */
@Composable
fun NotifSheet(
    open: Boolean,
    alerts: List<StockAlert>,
    onClose: () -> Unit,
    onOpen: (Stock) -> Unit,
) {
    val tokens = LocalMindstockTokens.current
    Sheet(open = open, title = stringResource(Res.string.notif_title), onClose = onClose) {
        Column {
            AppText(
                text = stringResource(Res.string.notif_subtitle),
                style = MindstockType.summarySub().copy(fontWeight = FontWeight.Normal, fontSize = 12.sp),
                color = tokens.faint,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                alerts.forEach { alert ->
                    AlertRow(alert = alert, onClick = { onClose(); onOpen(alert.stock) })
                }
            }
        }
    }
}

@Composable
private fun AlertRow(alert: StockAlert, onClick: () -> Unit) {
    val tokens = LocalMindstockTokens.current
    val (iconBg, iconColor) = when (alert.reason) {
        AlertReason.OutOfStock -> tokens.statusOutSoft to tokens.statusOut
        AlertReason.RunningLow -> tokens.statusLowSoft to tokens.statusLow
        is AlertReason.RunningOutSoon -> tokens.statusOkSoft to tokens.statusOk
    }
    val icon = if (alert.reason is AlertReason.OutOfStock) AppIconName.Cart else AppIconName.Trend
    val message = when (val reason = alert.reason) {
        AlertReason.OutOfStock -> stringResource(Res.string.notif_alert_out)
        AlertReason.RunningLow -> stringResource(Res.string.notif_alert_low)
        is AlertReason.RunningOutSoon -> stringResource(Res.string.notif_alert_soon, reason.days)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.radiusMd))
            .border(1.dp, tokens.lineSoft, RoundedCornerShape(tokens.radiusMd))
            .background(tokens.surface)
            .clickable(onClick = onClick)
            .padding(13.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(iconBg),
        ) {
            AppIcon(icon, contentDescription = null, tint = iconColor, size = 19.dp)
        }
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = alert.stock.product.name(),
                style = MindstockType.cardTitle().copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                color = tokens.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AppText(
                text = message,
                style = MindstockType.summarySub().copy(fontWeight = FontWeight.Medium, fontSize = 12.sp),
                color = tokens.faint,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        AppIcon(AppIconName.ChevronRight, contentDescription = null, tint = tokens.faint, size = 17.dp)
    }
}
```

> `AppIcon` の引数(`tint` / `size` / `contentDescription`)は `StockHomeScreen.kt:167` 付近や `NavIconButton` の使用例と同形。`Color` import は未使用なら削る(コンパイル警告)。`alert.stock.product.name()` は既存 `StockHomeScreen`/`ProductCard` と同じアクセス。

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinJs`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: コミット**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/notification/ui/NotifSheet.kt
git commit -m "feat(notification): お知らせシート NotifSheet を追加"
```

---

## Task 4: StockHomeScreen のベルを引数化

**Files:**
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/StockHomeScreen.kt`

- [ ] **Step 1: `StockHomeScreen` に引数追加**

`onOpenSettings: () -> Unit = {},`(62 行)の直後に追加:

```kotlin
    onBell: () -> Unit = {},
    hasAlerts: Boolean = false,
```

- [ ] **Step 2: `header` ラムダ内の `StockHeader(...)` 呼び出しに引き渡し**

`onOpenSettings = onOpenSettings,`(93 行付近)の直後に追加:

```kotlin
                    onBell = onBell,
                    hasAlerts = hasAlerts,
```

- [ ] **Step 3: `StockHeader` に引数追加**

private `StockHeader`(145 行付近)の `onOpenSettings: () -> Unit,` の直後に追加:

```kotlin
    onBell: () -> Unit,
    hasAlerts: Boolean,
```

- [ ] **Step 4: ベルの onClick/badge を差し替え(167 行)**

```kotlin
                NavIconButton(icon = AppIconName.Bell, contentDescription = "notifications", onClick = onBell, badge = hasAlerts)
```

- [ ] **Step 5: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinJs`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 6: コミット**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/StockHomeScreen.kt
git commit -m "feat(inventory): 在庫ホームのベルを onBell/hasAlerts で引数化"
```

---

## Task 5: InventoryRoute で onBell/hasAlerts を素通し

**Files:**
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/InventoryRoute.kt`

- [ ] **Step 1: `InventoryRoute` に引数追加**

`onOpenSettings: () -> Unit = {},` の直後に追加:

```kotlin
    onBell: () -> Unit = {},
    hasAlerts: Boolean = false,
```

- [ ] **Step 2: `StockHomeScreen(...)` 呼び出しに引き渡し**

`onOpenSettings = onOpenSettings,` の直後に追加:

```kotlin
        onBell = onBell,
        hasAlerts = hasAlerts,
```

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinJs`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: コミット**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/InventoryRoute.kt
git commit -m "feat(inventory): InventoryRoute に onBell/hasAlerts を素通し追加"
```

---

## Task 6: App.kt で配線(導出・状態・NotifSheet 配置)

**Files:**
- Modify: `frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/App.kt`

- [ ] **Step 1: import 追加**

ファイル先頭の import 群に追加:

```kotlin
import net.brightroom.mindstock.domain.model.inventory.stock.EvaluatedTime
import net.brightroom.mindstock.frontend.feature.inventory.InventoryUiState
import net.brightroom.mindstock.frontend.feature.notification.stockAlerts
import net.brightroom.mindstock.frontend.feature.notification.ui.NotifSheet
```

(`collectAsState` / `LaunchedEffect` / `getValue` / `mutableStateOf` / `remember` / `setValue` は既に import 済み。未 import のものがあればコンパイラの指示で追加。)

- [ ] **Step 2: `ReadyContent` 内で homeVm の状態購読・eager load・アラート導出・notifOpen 状態を追加**

`homeVm` 生成ブロック(400-411 行)の直後に追加:

```kotlin
    val homeState by homeVm.state.collectAsState()
    // ベルのバッジ/シートを全タブで正しくするため、Stock タブに入る前に在庫をロードする。
    LaunchedEffect(householdId) { homeVm.load() }
    val alerts =
        (homeState as? InventoryUiState.Content)
            ?.let { stockAlerts(it.stocks, EvaluatedTime.now()) }
            ?: emptyList()
    var notifOpen by remember(householdId) { mutableStateOf(false) }
```

- [ ] **Step 3: ベルを配線**

`AppShell(...)` の `onBell = {},`(441 行)を差し替え:

```kotlin
        onBell = { notifOpen = true },
```

`InventoryRoute(...)`(445-455 行)の `onOpenSettings = { selectedTab = Tab.Profile },` の直後に追加:

```kotlin
                onBell = { notifOpen = true },
                hasAlerts = alerts.isNotEmpty(),
```

- [ ] **Step 4: NotifSheet overlay を配置**

`AppShell(...)` ブロックの直後(閉じ `)` の後、`ReadyContent` 関数本体の末尾)に追加:

```kotlin
    NotifSheet(
        open = notifOpen,
        alerts = alerts,
        onClose = { notifOpen = false },
        onOpen = { stock ->
            notifOpen = false
            opened.value = DetailTarget(stock.product.id, stock)
        },
    )
```

> `opened`(`MutableState<DetailTarget?>`)と `DetailTarget` は `ReadyContent` の引数/既存 import。`stock.product.id` は商品 ID、第2引数 seed に `stock` を渡すと既存パターン(448 行 `DetailTarget(pid, seed)`)と一致。

- [ ] **Step 5: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinJs`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 6: コミット**

```bash
git add frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/App.kt
git commit -m "feat(notification): ベルから NotifSheet を開く配線とアラート導出を追加"
```

---

## Task 7: ビルド全体確認と render-verify

**Files:** なし(検証のみ)

- [ ] **Step 1: frontend テスト + コンパイル**

Run: `./gradlew :frontend:jsTest :frontend:compileKotlinJs`
Expected: BUILD SUCCESSFUL(StockAlertsTest 含め PASS)。

- [ ] **Step 2: render-verify(忠実度確認)**

`fidelity-verify-loop-mechanics` の手順で `NotifSheet` をモックと突合する。`?preview` ハーネス(`webMain/PreviewHarness.kt` + `Main.kt` の `?preview=` 分岐・未コミット)を一時追加し、`./gradlew :frontend:jsBrowserDevelopmentRun --continuous` で `NotifSheet` を単体描画 → mock `app/screens-c.jsx` の NotifSheet と side-by-side で以下を突合:
- アイコン箱: 38×38 / radius 11 / status soft 背景 / status color アイコン(out=Cart, それ以外=Trend)
- 行: padding 13 / radius=radiusMd / lineSoft ボーダー / surface 背景 / gap 9
- 商品名 600/14・メッセージ 500/12・faint・chevron 右端
- 副文 400/12・faint・下余白 16

差分があれば `NotifSheet.kt` の寸法を `.copy(...)` / dp 値で調整(本体ロジックは変えない)。ハーネスは検証後 `git checkout` + `rm` で撤去。

- [ ] **Step 3: 実機確認(可能なら)**

dev server で:
- 在庫に在庫切れ/残りわずか商品があるとき、モバイル幅でベルに赤ドットが点灯
- ベルタップで NotifSheet が開き、アラート行が出る
- 行タップでシートが閉じ、該当商品の詳細オーバーレイが開く
- アラートが無いときバッジ消灯・シートは行なし(副文のみ)

- [ ] **Step 4: render-verify で寸法調整した場合はコミット**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/notification/ui/NotifSheet.kt
git commit -m "fix(notification): render-verify で NotifSheet の寸法をモックに合わせる"
```

---

## Self-Review チェック結果

- **spec カバレッジ**: StockAlert/AlertReason(T1)・stockAlerts(T1)・NotifSheet(T3)・i18n 5件(T2)・ベル配線 mobile/desktop(T4/T5/T6)・バッジ動的化(T4/T6)・テスト(T1)・render-verify(T7)・スコープ外(backend/Web Push/設定トグル=触らない)を全タスクでカバー。
- **型整合**: `stockAlerts(stocks, asOf): List<StockAlert>` / `StockAlert(stock, reason)` / `AlertReason.{OutOfStock,RunningLow,RunningOutSoon(days)}` / `NotifSheet(open, alerts, onClose, onOpen)` / `StockHomeScreen` と `InventoryRoute` の `onBell`+`hasAlerts` が全タスクで一致。
- **プレースホルダ**: 本体コードに TODO 無し。テストヘルパ 4 関数のみ「Step 2 調査で実装」= 既存 Stock 生成手段に依存する正当な調査ステップ(本体の穴ではない)。
```
