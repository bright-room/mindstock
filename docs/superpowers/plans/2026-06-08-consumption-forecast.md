# 消費予測「あと約X日」 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 実際の消費履歴から推定したペースで「あと約X日で切れる」を在庫ホーム・買い物リストに表示する。

**Architecture:** `:domain` に純関数 `Stock.forecast(asOf)`（sealed `ConsumptionForecast` を返す）を追加。`ProductRpcService.list()` が既に全 `movements` を返すため RPC 変更・新インフラ・キャッシュ無しで frontend が即利用する。レート推定はトレーリング窓(60日)優先・履歴が浅い/直近窓に消費0なら全履歴 fallback。

**Tech Stack:** Kotlin Multiplatform（`:domain` common）、kotlinx-datetime（LocalDate 解像度の日数計算）、kotlin.test + Kotest assertions（commonTest）、Compose Multiplatform（frontend 表示）。

設計: `docs/superpowers/specs/2026-06-08-consumption-forecast-design.md`

---

## File Structure

- **Create** `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/ConsumptionForecast.kt` — 予測結果の sealed 型。
- **Modify** `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/movement/StockMovements.kt` — `consumptionRatePerDay(asOf)` 追加 + 訂正畳み込みを `latestCorrectionByTarget()` に抽出（`netQuantity` と共有）。
- **Modify** `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/Stock.kt` — `forecast(asOf)` 追加。
- **Create** `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/StockForecastTest.kt` — 全分岐テスト。
- **Modify** `frontend/src/commonMain/composeResources/values/strings.xml` — 予測文言。
- **Modify** `frontend/.../feature/inventory/ui/ProductCard.kt` — 「· あと約X日」。
- **Create** `frontend/.../feature/inventory/ui/ForecastBanner.kt` — ホームの予測バナー。
- **Modify** `frontend/.../feature/inventory/ui/StockHomeScreen.kt` — ForecastBanner を SummaryStrip 下に配置。
- **Modify** `frontend/.../feature/shopping/ui/ShoppingListScreen.kt` — 行の「あと約X日」。

---

## Task 1: `ConsumptionForecast` sealed 型

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/ConsumptionForecast.kt`

- [ ] **Step 1: 型を作成**

```kotlin
package net.brightroom.mindstock.domain.model.inventory.stock

import kotlinx.serialization.Serializable

/**
 * 消費予測の結果。nullable 戻り値原則禁止に従い、「予測不可」を null でなく型で表す。
 */
@Serializable
sealed interface ConsumptionForecast {
    /** 予測不可。消費実績が無い／現在在庫が 0 以下。 */
    @Serializable
    data object Unknown : ConsumptionForecast

    /** 現在のペースであと約 days 日で在庫が尽きる見込み。 */
    @Serializable
    data class DaysRemaining(val days: Int) : ConsumptionForecast
}
```

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :domain:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/ConsumptionForecast.kt
git commit -m "feat(domain): 消費予測結果 ConsumptionForecast を追加"
```

---

## Task 2: レート推定と `Stock.forecast`（TDD）

**Files:**
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/movement/StockMovements.kt`
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/Stock.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/StockForecastTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

`StockForecastTest.kt` を新規作成。`asOf` 固定、occurredAt はバックデートで構築する。仕様の具体例 A〜F + 窓境界 + 0除算クランプを網羅。

```kotlin
package net.brightroom.mindstock.domain.model.inventory.stock

import io.kotest.matchers.shouldBe
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.ProductStatus
import net.brightroom.mindstock.domain.model.inventory.product.image.ProductImage
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.product.setting.StockingPolicy
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementIdentity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Reason
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement.Consumption
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement.Replenishment
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import kotlin.test.Test

class StockForecastTest {
    private val asOf = LocalDateTime(2026, 6, 8, 12, 0)

    private fun actor() = Resident(ResidentId.create(), Profile(DisplayName("おや")))

    private fun product() =
        Product(
            id = ProductId.create(),
            name = ProductName("米"),
            barcode = Barcode.Unlinked,
            setting = StockingPolicy(ProductUnit("袋"), MinimumStock(1)),
            image = ProductImage.None,
            status = ProductStatus.採用中,
        )

    /** asOf の n 日前(正午)の OccurredAt。 */
    private fun daysAgo(n: Int): OccurredAt =
        OccurredAt(LocalDateTime(asOf.date.minus(DatePeriod(days = n)), LocalTime(12, 0)))

    private fun replenish(id: Long, qty: Int, daysAgo: Int) =
        Replenishment(MovementIdentity.Persisted(MovementId(id)), Quantity(qty), daysAgo(daysAgo), actor(), Note(""))

    private fun consume(id: Long, qty: Int, daysAgo: Int) =
        Consumption(MovementIdentity.Persisted(MovementId(id)), Quantity(qty), daysAgo(daysAgo), actor(), Note(""))

    private fun stockOf(vararg movements: StockMovement) =
        Stock(product(), StockMovements(movements.toList()))

    @Test
    fun A_定常はトレーリング窓のレートで予測する() {
        // 補充15(120日前)・消費12(30日前=直近60日内)→ 在庫3・span120・recent12 → rate=12/60=0.2 → 3/0.2=15
        val stock = stockOf(replenish(1, 15, 120), consume(2, 12, 30))
        stock.currentQuantity() shouldBe 3
        stock.forecast(asOf) shouldBe ConsumptionForecast.DaysRemaining(15)
    }

    @Test
    fun B_直近窓に消費が無ければ全履歴平均にfallbackする() {
        // 補充7(100日前)・消費5(80日前=窓外)→ 在庫2・span100・recent0 → rate=5/100=0.05 → 2/0.05=40
        val stock = stockOf(replenish(1, 7, 100), consume(2, 5, 80))
        stock.currentQuantity() shouldBe 2
        stock.forecast(asOf) shouldBe ConsumptionForecast.DaysRemaining(40)
    }

    @Test
    fun C_履歴の浅い新商品はspanベースで予測する() {
        // 補充10(10日前)・消費6(5日前)→ 在庫4・span10(<60) → rate=6/10=0.6 → round(4/0.6)=7
        val stock = stockOf(replenish(1, 10, 10), consume(2, 6, 5))
        stock.currentQuantity() shouldBe 4
        stock.forecast(asOf) shouldBe ConsumptionForecast.DaysRemaining(7)
    }

    @Test
    fun D_消費実績が無ければUnknown() {
        val stock = stockOf(replenish(1, 5, 10))
        stock.currentQuantity() shouldBe 5
        stock.forecast(asOf) shouldBe ConsumptionForecast.Unknown
    }

    @Test
    fun E_在庫が0以下ならUnknown() {
        // 補充3・消費3 → 在庫0
        val stock = stockOf(replenish(1, 3, 10), consume(2, 3, 5))
        stock.currentQuantity() shouldBe 0
        stock.forecast(asOf) shouldBe ConsumptionForecast.Unknown
    }

    @Test
    fun F_訂正後の実効消費量でレートを算出する() {
        // 補充20(20日前)・消費4(10日前,id10)を2に訂正 → 在庫18・実効消費2・span20(<60) → rate=2/20=0.1 → 18/0.1=180
        val stock =
            stockOf(replenish(1, 20, 20), consume(10, 4, 10))
                .correct(MovementId(10), Quantity(2), Reason("数え直し"), actor(), daysAgo(1))
        stock.currentQuantity() shouldBe 18
        stock.forecast(asOf) shouldBe ConsumptionForecast.DaysRemaining(180)
    }

    @Test
    fun 窓境界_ちょうど60日前の消費はトレーリングに含む() {
        // 補充100(120日前)・消費10(ちょうど60日前)→ 在庫90・span120・recent10(境界含む) → rate=10/60 → round(90/(10/60))=540
        val stock = stockOf(replenish(1, 100, 120), consume(2, 10, 60))
        stock.currentQuantity() shouldBe 90
        stock.forecast(asOf) shouldBe ConsumptionForecast.DaysRemaining(540)
    }

    @Test
    fun span1日クランプで0除算しない() {
        // 補充5・消費2 を同日(0日前)→ 在庫3・span=max(1,0)=1・span<60 → rate=2/1=2 → round(3/2)=2
        val stock = stockOf(replenish(1, 5, 0), consume(2, 2, 0))
        stock.currentQuantity() shouldBe 3
        stock.forecast(asOf) shouldBe ConsumptionForecast.DaysRemaining(2)
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "*StockForecastTest*"`
Expected: コンパイルエラー（`forecast` / `consumptionRatePerDay` 未定義）

- [ ] **Step 3: `StockMovements` にレート算出を実装**

`StockMovements.kt` を編集。(1) `netQuantity` 内の訂正畳み込みを private 関数に抽出して共有、(2) `consumptionRatePerDay` と窓定数を追加。

ファイル冒頭の import に追加:

```kotlin
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
```

`netQuantity()` の冒頭で組んでいた `latestCorrection` を private 関数へ抽出し、`netQuantity` はそれを呼ぶよう変更:

```kotlin
fun netQuantity(): Int {
    val latestCorrection = latestCorrectionByTarget()
    return list.sumOf { movement ->
        when (movement) {
            is Replenishment -> effectiveQuantity(movement, latestCorrection)
            is Consumption -> -effectiveQuantity(movement, latestCorrection)
            is Correction -> 0
        }
    }
}

/** 同一 target への訂正のうち最新(occurredAt 最大)を採る。 */
private fun latestCorrectionByTarget(): Map<MovementId, Correction> =
    list
        .filterIsInstance<Correction>()
        .groupBy { it.target }
        // 同一 target に同 occurredAt の訂正が複数ある場合は list 出現順で最初の最大値を採用(実運用では LocalDateTime(同時刻)衝突は起きない前提)
        .mapValues { (_, corrections) -> corrections.maxBy { it.occurredAt() } }
```

`effectiveQuantity` private 関数はそのまま残す。末尾に追加:

```kotlin
/**
 * 1 日あたりの消費ペース。消費(訂正反映後)が無ければ 0.0。
 * トレーリング窓(直近 FORECAST_WINDOW_DAYS 日)に消費があり履歴が窓を満たすならその窓レート、
 * そうでなければ全履歴平均(最初の movement→asOf を span とする)に fallback する。
 */
fun consumptionRatePerDay(asOf: LocalDateTime): Double {
    if (list.isEmpty()) return 0.0
    val corrections = latestCorrectionByTarget()
    val consumptions =
        list
            .filterIsInstance<Consumption>()
            .map { it.occurredAt() to effectiveQuantity(it, corrections) }
    val totalConsumed = consumptions.sumOf { it.second }
    if (totalConsumed == 0) return 0.0

    val firstDate = list.minOf { it.occurredAt().date }
    val spanDays = maxOf(1, firstDate.daysUntil(asOf.date))
    val windowStart = asOf.date.minus(DatePeriod(days = FORECAST_WINDOW_DAYS))
    val recentConsumed = consumptions.filter { it.first.date >= windowStart }.sumOf { it.second }

    return if (spanDays >= FORECAST_WINDOW_DAYS && recentConsumed > 0) {
        recentConsumed.toDouble() / FORECAST_WINDOW_DAYS
    } else {
        totalConsumed.toDouble() / spanDays
    }
}

companion object {
    /** トレーリング窓の日数。 */
    const val FORECAST_WINDOW_DAYS = 60
}
```

- [ ] **Step 4: `Stock` に `forecast` を実装**

`Stock.kt` の import に追加:

```kotlin
import kotlinx.datetime.LocalDateTime
import kotlin.math.roundToInt
```

`latestMovement()` の下に追加:

```kotlin
/**
 * 現在の消費ペースから「あと約何日で在庫が尽きるか」を予測する。
 * asOf は基準時刻(frontend は now-JST、テストは固定値)。
 * 在庫 0 以下・消費実績なしは Unknown。
 */
fun forecast(asOf: LocalDateTime): ConsumptionForecast {
    val quantity = currentQuantity()
    if (quantity <= 0) return ConsumptionForecast.Unknown
    val rate = movements.consumptionRatePerDay(asOf)
    if (rate <= 0.0) return ConsumptionForecast.Unknown
    return ConsumptionForecast.DaysRemaining((quantity / rate).roundToInt())
}
```

- [ ] **Step 5: テストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "*StockForecastTest*"`
Expected: PASS（8 テスト）

- [ ] **Step 6: 既存ドメインテストの非退行確認**

Run: `./gradlew :domain:jvmTest`
Expected: BUILD SUCCESSFUL（`StockMovementsTest`/`StockTest` 含め全 PASS。`netQuantity` リファクタの非退行確認）

- [ ] **Step 7: Commit**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/movement/StockMovements.kt \
        domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/Stock.kt \
        domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/StockForecastTest.kt
git commit -m "feat(domain): 消費履歴から Stock.forecast(あと約X日)を算出"
```

---

## Task 3: 予測文言の string リソース

**Files:**
- Modify: `frontend/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: 文言を追加**

`strings.xml` の適切な位置（在庫系の近く）に追加。`%1$d` は日数、バナーの `%1$s` は商品名。

```xml
<!-- 消費予測 -->
<string name="forecast_days_left">· あと約%1$d日</string>
<string name="forecast_banner">%1$s はあと約 %2$d 日で切れる予測です</string>
<string name="forecast_days_left_plain">あと約%1$d日</string>
```

（`forecast_days_left` = ProductCard 用の先頭中黒つき、`forecast_days_left_plain` = 買い物リスト行用、`forecast_banner` = ホームのトレンドバナー用。）

- [ ] **Step 2: リソース生成確認**

Run: `./gradlew :frontend:generateComposeResClass`
Expected: BUILD SUCCESSFUL（`Res.string.forecast_days_left` 等が生成される）

- [ ] **Step 3: Commit**

```bash
git add frontend/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(frontend): 消費予測の string リソースを追加"
```

---

## Task 4: ProductCard に「· あと約X日」

**Files:**
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/ProductCard.kt`

モック `screens-a.jsx:111`: StatusDot の右に `gap 8`・`500 12px/1` の faint 色「· あと約X日」を、`days !== null && qty > 0` のとき表示。

- [ ] **Step 1: forecast を読み、StatusDot を Row でラップして文言を足す**

import 追加:

```kotlin
import androidx.compose.foundation.layout.Row
import net.brightroom.mindstock.domain.model.inventory.stock.ConsumptionForecast
import net.brightroom.mindstock.extensions.kotlinx.datetime.now
import kotlinx.datetime.LocalDateTime
import mindstock.frontend.generated.resources.forecast_days_left
```

`StatusDot(...)` の行（`Spacer(Modifier.height(6.dp))` の直後）を次に置き換える:

```kotlin
Row(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    StatusDot(color = statusColor, soft = statusSoft, label = statusLabel)
    val forecast = stock.forecast(LocalDateTime.now())
    if (forecast is ConsumptionForecast.DaysRemaining) {
        AppText(
            stringResource(Res.string.forecast_days_left, forecast.days),
            style = MindstockType.unitCaption(),
            color = tokens.faint,
            maxLines = 1,
        )
    }
}
```

（`qty > 0` 条件は `forecast` が `Unknown`（在庫0で Unknown）になるため `DaysRemaining` 分岐で自動的に満たされる。`MindstockType.unitCaption()` がモックの `500 12px` 相当。実描画で字間/サイズを後段で突合。）

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/ProductCard.kt
git commit -m "feat(frontend): ProductCard に消費予測あと約X日を表示"
```

---

## Task 5: ホームの予測バナー（ForecastBanner）

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/ForecastBanner.kt`
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/StockHomeScreen.kt`

モック `screens-a.jsx:81-87`: SummaryStrip の下に、在庫 > 0 で `DaysRemaining` の中から **最小 days** の商品を選び「**◯◯** はあと約 X 日で切れる予測です」。該当無しなら非表示。装飾: `padding 11px 16px`・`radius md`・`surface` 背景・`1px lineSoft` ボーダー・trend アイコン(accent)・商品名のみ 700/ink、本文 sub。

- [ ] **Step 1: ForecastBanner を作成**

```kotlin
package net.brightroom.mindstock.frontend.feature.inventory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDateTime
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.forecast_banner
import net.brightroom.mindstock.domain.model.inventory.stock.ConsumptionForecast
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.extensions.kotlinx.datetime.now
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import org.jetbrains.compose.resources.stringResource

/**
 * 在庫ホームの予測バナー。在庫 > 0 で最も早く切れる見込みの商品を 1 件表示する。
 * 予測可能な商品が無ければ何も描画しない。
 */
@Composable
fun ForecastBanner(
    stocks: Stocks,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMindstockTokens.current
    val now = LocalDateTime.now()
    // 在庫 > 0 かつ DaysRemaining の中で最小 days
    val soon =
        stocks.list
            .mapNotNull { stock ->
                when (val f = stock.forecast(now)) {
                    is ConsumptionForecast.DaysRemaining -> stock to f.days
                    ConsumptionForecast.Unknown -> null
                }
            }
            .minByOrNull { it.second } ?: return

    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(tokens.surface)
                .border(1.dp, tokens.lineSoft, shape)
                .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(AppIconName.Trend, contentDescription = null, size = 17.dp, tint = tokens.accent)
        AppText(
            stringResource(Res.string.forecast_banner, soon.first.product.name().substringBefore(' '), soon.second),
            style = MindstockType.sectionMeta(),
            color = tokens.sub,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
```

（モックは商品名先頭語のみ太字 ink・残りは sub だが、Compose の単一 `AppText` ではインライン太字が煩雑なため v1 は全文 sub・先頭語切出し `substringBefore(' ')` で「商品名 はあと約…」とする。商品名強調は render 突合時に `AnnotatedString` 化を検討。`radius md` は token 16dp 相当。）

- [ ] **Step 2: StockHomeScreen に配置**

`StockHomeScreen.kt` の import に追加:

```kotlin
// (ForecastBanner は同パッケージなので import 不要)
```

`SummaryStrip(summary = summary, onClick = onShop)` の直後（同じ `if (state.query.isBlank())` ブロック内）に追加:

```kotlin
if (state.query.isBlank()) {
    val summary = stockSummaryOf(state.stocks.list.map { it.status() })
    SummaryStrip(summary = summary, onClick = onShop)
    ForecastBanner(stocks = state.stocks)
}
```

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/ForecastBanner.kt \
        frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/StockHomeScreen.kt
git commit -m "feat(frontend): 在庫ホームに消費予測バナーを表示"
```

---

## Task 6: 買い物リスト行に「あと約X日」

**Files:**
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/shopping/ui/ShoppingListScreen.kt`

モック `screens-c.jsx:210`: 行内で `days !== null && qty > 0` のとき accent 色 `600` の「あと約X日」。`StatusDot(...)` 付近（行 338）に追加する。

- [ ] **Step 1: 該当行を確認**

Run: `sed -n '320,345p' frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/shopping/ui/ShoppingListScreen.kt`
Expected: `StatusDot(color = statusColor, soft = statusSoft, label = statusLabel)`（行 338 付近）と、その親 Column/Row 構造が見える

- [ ] **Step 2: forecast 文言を追加**

import に追加:

```kotlin
import net.brightroom.mindstock.domain.model.inventory.stock.ConsumptionForecast
import net.brightroom.mindstock.extensions.kotlinx.datetime.now
import kotlinx.datetime.LocalDateTime
import mindstock.frontend.generated.resources.forecast_days_left_plain
```

`StatusDot(...)`（行 338 付近）の直後に、StatusDot と同じ Column/Row 内へ追加:

```kotlin
val forecast = stock.forecast(LocalDateTime.now())
if (forecast is ConsumptionForecast.DaysRemaining) {
    AppText(
        stringResource(Res.string.forecast_days_left_plain, forecast.days),
        style = MindstockType.statusLabel().copy(fontWeight = FontWeight.SemiBold),
        color = tokens.accent,
    )
}
```

（`FontWeight` は既に import 済み（行 251 で使用）。`tokens.accent` が モックの accent 色。配置先の縦/横は Step 1 で見た構造に合わせ、StatusDot と同じ親に置く。render 突合で位置を確定。）

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/shopping/ui/ShoppingListScreen.kt
git commit -m "feat(frontend): 買い物リスト行に消費予測あと約X日を表示"
```

---

## Task 7: フルビルド + render 確認

**Files:** なし（検証のみ）

- [ ] **Step 1: 全モジュールビルド + テスト**

Run: `./gradlew :domain:jvmTest :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: dev server で render 確認**

`fidelity-verify-loop-mechanics` の手順で `:frontend:jsBrowserDevelopmentRun --continuous` を起動し、在庫ホーム（ProductCard の「· あと約X日」・予測バナー）と買い物リスト（行の「あと約X日」）の**表示の有無とレイアウト**をモック `screens-a.jsx` / `screens-c.jsx` と side-by-side で突合する。

**注意:** 数値そのもの（X 日）はモックの静的 `daily` と一致しない＝この項目では正常。一致を狙わない（spec「確定事項」参照）。Unknown 商品で要素が出ないこと、`DaysRemaining` で文言が出ることを確認する。

- [ ] **Step 3: 最終 commit（必要なら render 微修正後）**

render 突合で字間/位置の微修正が出たら修正してコミット。差分が無ければスキップ。

```bash
git commit -am "fix(frontend): 消費予測表示の render 突合微修正"
```

---

## Self-Review

**Spec coverage:**
- ✅ 純ドメイン `Stock.forecast` / sealed `ConsumptionForecast` → Task 1, 2
- ✅ トレーリング優先・全履歴 fallback・W=60・0除算クランプ → Task 2（具体例 A〜F + 境界 + クランプ）
- ✅ 消費のみ・訂正 effective 反映 → Task 2 F テスト + `latestCorrectionByTarget` 共有
- ✅ Unknown 非表示 → Task 4/5/6 の `is DaysRemaining` 分岐
- ✅ StockHome バナー + ProductCard → Task 4, 5
- ✅ ShoppingList 行 → Task 6
- ✅ RPC 変更なし → 全 Task で RPC/Repository 不変
- ✅ モック数値不一致は正常 → Task 7 注意書き
- 注: spec はアラート文言「あと約X日で切れそうです」(screens-c.jsx:324) にも言及するが、実装の ShoppingList に該当アラートセクションが現状無いため、行レベルの「あと約X日」(Task 6) に集約。アラートセクション新設は本予測の責務でなくモック忠実化の別論点（監査 §5）として render 突合時に判断。

**Placeholder scan:** なし（全 step に実コード/実コマンド）。

**Type consistency:** `ConsumptionForecast.{Unknown, DaysRemaining(days)}`・`Stock.forecast(asOf: LocalDateTime)`・`StockMovements.consumptionRatePerDay(asOf)`・`FORECAST_WINDOW_DAYS` を全 Task で一貫使用。`LocalDateTime.now()` は `:shared` の JST 拡張。
