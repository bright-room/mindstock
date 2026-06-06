# P6-1a 見た目忠実化 + デザインシステム整備 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** mindstock frontend の在庫画面と共有 atom を、参照モック(`docs/ref/mindstock.zip`)に視覚的に忠実な状態へ引き上げる。

**Architecture:** token → atom → screen の一方向。`designsystem/theme` に影ヘルパとタイポグラフィ プリセットを足し、`designsystem/atom` の素の Material3 ラッパをモック忠実な実装へ差し替え、`feature/inventory` の StockHome に SummaryStrip とヘッダ chrome を追加する。アプリ配線(App.kt→AppShell→InventoryRoute)は既に通っているので配線変更は不要。

**Tech Stack:** Kotlin Multiplatform / Compose Multiplatform(Kotlin/Wasm)/ Material3 Expressive 1.10.0-alpha05 / material-icons-extended 1.7.3 / Compose Resources。clay テーマ・NotoSansJP。

**Scope fences:** clay + mobile のみ。indigo/pine・角丸 variant・desktop サイドバー・tweaks パネルは作らない。消費予測の実ロジックと手動 wanted フラグ配線は P6-1b(本パスでは暫定/非表示)。

**規約(必読):** `.claude/rules/frontend-designsystem.md`(feature は material3 を直接 import しない・atom 経由)/ `frontend-i18n-and-font.md`(文言は strings.xml)/ `frontend-compose-conventions.md`(commonTest は **Kotest FunSpec 不可**、`kotlin.test.@Test` + `io.kotest.matchers.*`)/ `frontend-kmp-structure.md`(web 固有 API を commonMain に直書きしない)。

**参照値の出所:** 色は既に `MindstockTheme.kt`(ClayColorScheme)/ `MindstockTokens.kt`(status)に sRGB 変換済み。寸法/ウェイト/レイアウトは `docs/ref/mindstock.zip` 展開後の `app/core.jsx`・`app/screens-a.jsx` の実数値をそのまま使う。

**検証コマンド:**
- コンパイル: `./gradlew :frontend:compileKotlinWasmJs`(フルビルドは OOM。これを使う)
- テスト: `./gradlew :frontend:wasmJsTest`(commonTest は wasmJs で走る)
- dev server: `./gradlew :frontend:wasmJsBrowserDevelopmentRun`
- スクショ: webapp-testing スキル(Playwright)

---

## File Structure

**新規作成:**
- `frontend/src/commonMain/kotlin/.../designsystem/theme/Shadow.kt` — `Modifier.softShadow(level)` + `ShadowLevel`
- `frontend/src/commonMain/kotlin/.../designsystem/theme/MindstockType.kt` — `TextStyle` プリセット
- `frontend/src/commonMain/kotlin/.../designsystem/atom/SearchField.kt`
- `frontend/src/commonMain/kotlin/.../designsystem/atom/HouseholdPill.kt`
- `frontend/src/commonMain/kotlin/.../designsystem/atom/AddTile.kt`
- `frontend/src/commonMain/kotlin/.../feature/inventory/ui/SummaryStrip.kt`
- `frontend/src/commonMain/kotlin/.../feature/inventory/ui/CompactCard.kt`
- `frontend/src/commonMain/kotlin/.../feature/inventory/StockSummary.kt` — 集計の純関数
- `frontend/src/commonMain/kotlin/.../feature/inventory/ProductGlyph.kt` — 名前→アイコンのヒューリスティック(純関数)
- `frontend/src/commonTest/kotlin/.../designsystem/atom/StockLevelMathTest.kt`
- `frontend/src/commonTest/kotlin/.../feature/inventory/StockSummaryTest.kt`
- `frontend/src/commonTest/kotlin/.../feature/inventory/ProductGlyphTest.kt`

**差し替え(中身を忠実度アップグレード):**
- `designsystem/atom/AppText.kt`(style/color 引数追加)
- `designsystem/atom/PrimaryButton.kt`(→ AppButton: variant/size/icon)
- `designsystem/atom/Thumb.kt` / `StatusDot.kt` / `StockLevelBar.kt` / `SegmentedControl.kt` / `Stepper.kt` / `RoundBtn.kt` / `Sheet.kt` / `Toast.kt` / `AppIcon.kt`(glyph 追加)

**修正:**
- `designsystem/theme/MindstockTokens.kt`(影 elevation 値・補助色を追加)
- `feature/inventory/ui/ProductCard.kt`(restyle)
- `feature/inventory/ui/StockHomeScreen.kt`(ヘッダ/SummaryStrip/件数+seg/grid・list/AddTile)
- `frontend/src/commonMain/composeResources/values/strings.xml`(文言追加)

各ファイルは 1 責務。atom は引数+コールバック(state hoisting)を守る。

---

## Task 0: 検証基盤の確認(着手前ゲート)

**Files:** なし(実行確認のみ)

- [ ] **Step 1: モックを展開してブラウザ表示の準備**

Run:
```bash
cd /tmp && rm -rf mindstock_mock && mkdir mindstock_mock && cd mindstock_mock && \
  unzip -o /Users/nonaka.koki/dev/ghq/github.com/bright-room/mindstock/docs/ref/mindstock.zip >/dev/null && ls app/
```
Expected: `app.jsx core.jsx data.jsx screens-a.jsx ...` が並ぶ。
（モックは React/JSX。視覚比較は「dev server で実アプリを起動」⇄「core.jsx/screens-a.jsx の実数値」で行う。zip 内に HTML エントリがあればブラウザでも開く。）

- [ ] **Step 2: dev server が起動することを実証**

Run: `./gradlew :frontend:wasmJsBrowserDevelopmentRun`
Expected: コンパイル成功し、`http://localhost:8080`(or 表示された port)で serve される。ブラウザで開いて在庫画面(または未ログイン時は loading/onboarding)が出ることを確認。
※メモリの「WasmJs OOM」は**フルビルド**の話。dev server は別経路。ここで起動を確認してから後続で依存する。起動しない場合はここで原因を潰す（後続タスクの視覚検証が全て依存するため）。

- [ ] **Step 3: ベースラインのスクショを撮る**

webapp-testing スキルで現状(ダサい状態)のスクショを保存し、Before として残す。各 atom タスク後の After と比較する基準にする。

---

## Task 0.5: サンプルデータのプレビュー経路(検証の土台・必須)

**Files:**
- Create: `frontend/src/commonMain/kotlin/.../feature/inventory/ui/StockHomePreview.kt`
- Modify(検証中のみ・最後に戻す): `frontend/src/webMain/kotlin/.../App.kt`

**なぜ必須か:** 当初の失敗は「モックと比較せず出荷した」。Task 16 のスクショ比較は、dev server が
**実際に商品カード入りの StockHome に到達**して初めて意味を持つ。本番経路は Zitadel ログイン + 登録ユーザ +
アクティブ世帯 + backend RPC が `Stocks` を返す、を全て満たす必要があり、見た目の高速反復には不向き。
`InventoryViewModel` は `loadStocks: suspend (HouseholdId) -> RpcOutcome<Stocks>` を**注入で受ける**ので、
サンプルを返すラムダを差せば認証/backend 無しでカード描画画面を出せる。**全タスクのスクショはこの経路で撮る。**
（注: Wasm では `@Preview` は IDE 限定で dev server には出ない。実際に描画される代替 composable/エントリにする。)

- [ ] **Step 1: サンプル Stocks ファクトリを作る**

`StockHomePreview.kt`(out/low/ok の混在、長い名前、grid/list 両方で確認できる構成)。`Stock` 構築は
`domain` の `Stock(product, movements)` を組む。構築方法は既存 `commonTest` の `InventoryViewModelTest` /
`ProductDetailViewModelTest` のファクトリを参照し、同じやり方で `Product`(`ProductName`/`StockingPolicy`/`ProductUnit`/
`MinimumStock`/`Barcode`/`ProductImage`/`ProductStatus`)+ `StockMovements`(補充/消費を積んで netQuantity を作る)を組む。
```kotlin
package net.brightroom.mindstock.frontend.feature.inventory.ui

import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks

/** 認証/backend 無しの見た目検証用サンプル。net 数量で out/low/ok を作り分ける。 */
fun previewStocks(): Stocks = Stocks(
    listOf(
        // 例: 牛乳 net0(out, min2) / トイレットペーパー net1(low, min1) / 食器用洗剤 net3(ok, min1)
        // … 実際の Product/StockMovements 構築は既存テストのファクトリ流用
    ),
)
```
（具体的な構築コードは `InventoryViewModelTest` の既存ヘルパをコピーして埋める。netQuantity が min を割る/割らないで status が決まる。)

- [ ] **Step 2: App.kt に検証用の差し込み(後で戻す)**

`App.kt` の `AuthState.Ready` 分岐冒頭に、検証時だけ有効化する分岐を置く(環境フラグ or 一時的なハードコード)。
`InventoryViewModel(householdId=<任意>, loadStocks = { RpcOutcome.Success(previewStocks()) }, ... )` を渡して
`InventoryRoute`/`StockHomeScreen` をサンプルで描画する。**この差し込みは検証用で、Task 16 完了後に元へ戻す**
(コミットには含めない。`git stash` 等で退避するか、別ブランチで検証)。

- [ ] **Step 3: dev server で商品カードが出ることを確認**

Run: `./gradlew :frontend:wasmJsBrowserDevelopmentRun`
Expected: ログイン無しで在庫一覧(現状はまだダサい)が、サンプル商品カード入りで表示される。
**ここに到達できない場合、後続の視覚検証が全て成立しないので、ここで必ず解消する。**

（コミットなし＝検証足場。サンプル composable 自体(`StockHomePreview.kt`)は残してよい。)

---

## Task 1: Token 層 — 影スケールと softShadow ヘルパ

**Files:**
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/theme/MindstockTokens.kt`
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/theme/Shadow.kt`

モックの影は CSS 多層 box-shadow。Compose の `Modifier.shadow` は単一 elevation。**近似を 1 つのヘルパに封じ込め**、atom は生 `shadow` を使わない。

- [ ] **Step 1: ShadowLevel と softShadow を作る**

Create `Shadow.kt`:
```kotlin
package net.brightroom.mindstock.frontend.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** モックの影スケール(sm/md/lg/pop)を Compose の単層 shadow で近似する。 */
enum class ShadowLevel(val elevation: Dp) {
    Sm(2.dp),
    Md(8.dp),
    Lg(18.dp),
    Pop(26.dp),
}

// モックの影は暖色がかった半透明黒(rgba(40,33,28,*))。ambient/spot をそれに寄せる。
private val ShadowTint = Color(0xFF28211C)

fun Modifier.softShadow(
    level: ShadowLevel,
    shape: Shape = RoundedCornerShape(22.dp),
): Modifier =
    this.shadow(
        elevation = level.elevation,
        shape = shape,
        clip = false,
        ambientColor = ShadowTint,
        spotColor = ShadowTint,
    )
```

- [ ] **Step 2: MindstockTokens に semantic 色を全部入れる(feature が material3 を直参照しないため)**

`frontend-designsystem.md` は **`feature/**` が `androidx.compose.material3.*` を直接 import するのを禁止**している。
よって feature ファイル(SummaryStrip / ProductCard / CompactCard / StockHomeScreen)が必要な色を **すべて `MindstockTokens` に持たせ**、
feature は `LocalMindstockTokens.current` からのみ色を取る。`MaterialTheme.colorScheme` の直参照は `designsystem/`(atom)内に閉じる。

`MindstockTokens.kt` の `data class MindstockTokens(...)` に以下を追加(値は `MindstockTheme.kt` の ClayColorScheme と core.jsx clay TONES より):
```kotlin
// data class に追加(status*/radius は既存)
val accent: Color,
val onAccent: Color,
val accentSoft: Color,
val surface: Color,
val surface2: Color,
val ink: Color,
val sub: Color,
val line: Color,
val lineSoft: Color,
val faint: Color,
```
`clayTokens` に追加:
```kotlin
accent = Color(0xFFC76743),
onAccent = Color(0xFFFFFBF4),
accentSoft = Color(0xFFFFE3D3),
surface = Color(0xFFFFFDFA),
surface2 = Color(0xFFFBF7F3),
ink = Color(0xFF2B2520),
sub = Color(0xFF69625C),
line = Color(0xFFE1DDD8),
lineSoft = Color(0xFFEAE7E4),
faint = Color(0xFFA59C94), // oklch(0.66 0.010 60)
```

**色参照の対応表(本プラン共通):** 以降のコードブロックで、**feature ファイル**(`feature/inventory/**`)に出てくる
`MaterialTheme.colorScheme.X` は、実装時にすべて下表の `tokens.Y` に読み替える(`val tokens = LocalMindstockTokens.current` を先頭に置く)。
**atom ファイル**(`designsystem/atom/**`)は規約上 material3 を直接使ってよいので `MaterialTheme.colorScheme.X` のままでよい。

| MaterialTheme.colorScheme | tokens(feature で使う) |
| --- | --- |
| `primary` | `accent` |
| `onPrimary` | `onAccent` |
| `primaryContainer` | `accentSoft` |
| `surface` | `surface` |
| `surfaceVariant` | `surface2` |
| `onSurface` | `ink` |
| `onSurfaceVariant` | `sub` |
| `outline` | `line` |
| `outlineVariant` | `lineSoft` |

（この一括ルールにより、後続の feature コードブロックは個別注記なしで規約準拠にできる。)

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/theme/Shadow.kt \
        frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/theme/MindstockTokens.kt
git commit -m "feat(frontend): 影スケール softShadow ヘルパと faint トークンを追加"
```

---

## Task 2: タイポグラフィ プリセット + AppText の style 対応

**Files:**
- Create: `frontend/src/commonMain/kotlin/.../designsystem/theme/MindstockType.kt`
- Modify: `frontend/src/commonMain/kotlin/.../designsystem/atom/AppText.kt`

`AppText` が `style` を持たず全テキストが Material 既定なのが「見出しが効かない」主因。モック頻出スタイルをプリセット化する。

- [ ] **Step 1: MindstockType を作る**

Create `MindstockType.kt`(値は core.jsx / screens-a.jsx の `font:` 指定より):
```kotlin
package net.brightroom.mindstock.frontend.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** モック core.jsx の font 指定をプリセット化。AppText(style = MindstockType.xxx) で使う。 */
@Immutable
object MindstockType {
    private fun base(family: androidx.compose.ui.text.font.FontFamily) = TextStyle(fontFamily = family)

    @Composable
    fun screenTitle() = base(notoSansJpFamily()).copy(fontWeight = FontWeight.ExtraBold, fontSize = 25.sp, letterSpacing = (-0.02).em) // 800/25
    @Composable
    fun greeting() = base(notoSansJpFamily()).copy(fontWeight = FontWeight.Medium, fontSize = 13.sp) // 500/13 faint
    @Composable
    fun cardTitle() = base(notoSansJpFamily()).copy(fontWeight = FontWeight.SemiBold, fontSize = 15.5.sp) // 600/15.5
    @Composable
    fun bigQty() = base(notoSansJpFamily()).copy(fontWeight = FontWeight.Bold, fontSize = 30.sp, fontFeatureSettings = "tnum") // 700/30 等幅数字
    @Composable
    fun unitCaption() = base(notoSansJpFamily()).copy(fontWeight = FontWeight.Medium, fontSize = 11.5.sp) // 500/11.5
    @Composable
    fun statusLabel() = base(notoSansJpFamily()).copy(fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp) // 600/12.5
    @Composable
    fun summaryTitle() = base(notoSansJpFamily()).copy(fontWeight = FontWeight.Bold, fontSize = 16.sp) // 700/16
    @Composable
    fun summarySub() = base(notoSansJpFamily()).copy(fontWeight = FontWeight.Medium, fontSize = 12.5.sp) // 500/12.5
    @Composable
    fun sectionMeta() = base(notoSansJpFamily()).copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp) // 600/13 sub
    @Composable
    fun button() = base(notoSansJpFamily()).copy(fontWeight = FontWeight.SemiBold, fontSize = 15.5.sp) // 600/15.5
}
```
注: `notoSansJpFamily()` は `@Composable`(`Typography.kt` 既存)。プリセットも `@Composable fun` にして呼び出し時に解決する。`em` letterSpacing は `androidx.compose.ui.unit.em`。

- [ ] **Step 2: AppText に style/color を足す(後方互換)**

`AppText.kt` を差し替え:
```kotlin
package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

/** feature 層が material3.Text を直接 import しないための薄いラッパ。 */
@Composable
fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(text = text, modifier = modifier, style = style, color = color, maxLines = maxLines, overflow = overflow)
}
```
（既存呼び出しは `style`/`color` 省略で従来どおり動く＝後方互換。）

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/theme/MindstockType.kt \
        frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/AppText.kt
git commit -m "feat(frontend): タイポグラフィ プリセットと AppText の style 対応"
```

---

## Task 3: AppButton(Btn variant/size/icon)

**Files:**
- Modify: `frontend/src/commonMain/kotlin/.../designsystem/atom/PrimaryButton.kt`

core.jsx `Btn`: variant(primary/soft/ghost/quiet/danger)・size(sm/md/lg)・icon・押下 scale。既存 `PrimaryButton(onClick, modifier, enabled, content)` の呼び出し(ProductCard/MoveSheet/ProductDetail)を壊さないため、`PrimaryButton` はそのまま薄く残しつつ新 `AppButton` を同ファイルに足す。

- [ ] **Step 1: AppButton を実装(PrimaryButton.kt に追記)**

`PrimaryButton.kt` を差し替え:
```kotlin
package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType

enum class ButtonVariant { Primary, Soft, Ghost, Quiet, Danger }
enum class ButtonSize(val height: Int, val radius: Int) { Sm(38, 12), Md(50, 15), Lg(56, 17) }

/** 既存呼び出し互換の primary ボタン(中身は AppButton)。 */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = AppButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)

@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    size: ButtonSize = ButtonSize.Md,
    icon: AppIconName? = null,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val tokens = LocalMindstockTokens.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.965f else 1f, label = "btnScale")

    val (container, contentColor, border) = when (variant) {
        ButtonVariant.Primary -> Triple(scheme.primary, scheme.onPrimary, null)
        ButtonVariant.Soft -> Triple(scheme.primaryContainer, scheme.primary, null)
        ButtonVariant.Ghost -> Triple(scheme.surface, scheme.onSurface, BorderStroke(1.dp, scheme.outline))
        ButtonVariant.Quiet -> Triple(scheme.surfaceVariant, scheme.onSurfaceVariant, null)
        ButtonVariant.Danger -> Triple(tokens.statusOutSoft, tokens.statusOut, null)
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = RoundedCornerShape(size.radius.dp),
        border = border,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = contentColor),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
        modifier = modifier.height(size.height.dp).scale(scale),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) AppIcon(icon, contentDescription = null, modifier = Modifier.padding(end = 0.dp))
            ProvideButtonTextStyle { content() }
        }
    }
}

@Composable
private fun ProvideButtonTextStyle(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.material3.LocalTextStyle provides MindstockType.button(),
        content = content,
    )
}
```
注:
- `Triple` の分解代入は val 3 つに。`AppIcon` の size 引数は Task 10 で追加。
- **Material3 `Button` は `defaultMinSize`(≈48dp の最小タッチ領域)を持つため、`Sm`=38dp が効かないことがある。**
  `Modifier.height(size.height.dp)` だけでは縮まない場合、`Modifier.requiredHeight(size.height.dp)` を使うか、
  `Surface`/`Box`+`clickable` ベースの自前ボタンにする。Sm の 38dp 実寸はスクショで確認すること。

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL。既存 `PrimaryButton` 呼び出しは互換。

- [ ] **Step 3: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/PrimaryButton.kt
git commit -m "feat(frontend): AppButton(variant/size/icon/押下スケール)を追加"
```

---

## Task 4: StockLevelBar の純関数を切り出してテスト(TDD)

**Files:**
- Create: `frontend/src/commonMain/kotlin/.../designsystem/atom/StockLevelMath.kt`
- Create: `frontend/src/commonTest/kotlin/.../designsystem/atom/StockLevelMathTest.kt`

描画は視覚検証だが、充填率の計算はロジックなので TDD する。

- [ ] **Step 1: 失敗するテストを書く**

Create `StockLevelMathTest.kt`:
```kotlin
package net.brightroom.mindstock.frontend.designsystem.atom

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class StockLevelMathTest {
    @Test
    fun comfortable_is_at_least_min_times_two() {
        comfortableStock(qty = 0, min = 2) shouldBe 4
    }

    @Test
    fun comfortable_uses_min_plus_three_when_larger() {
        comfortableStock(qty = 0, min = 1) shouldBe 4 // max(2, 4, 1, 1)
    }

    @Test
    fun comfortable_never_below_qty() {
        comfortableStock(qty = 10, min = 1) shouldBe 10
    }

    @Test
    fun fill_fraction_is_qty_over_comfortable_clamped() {
        fillFraction(qty = 2, min = 1).toDouble() shouldBe (0.5 plusOrMinus 0.0001) // 2/4
        fillFraction(qty = 0, min = 1).toDouble() shouldBe (0.0 plusOrMinus 0.0001)
    }

    @Test
    fun min_marker_fraction_is_min_over_comfortable() {
        minFraction(qty = 0, min = 1).toDouble() shouldBe (0.25 plusOrMinus 0.0001) // 1/4
    }
}
```

- [ ] **Step 2: テストが落ちることを確認**

Run: `./gradlew :frontend:wasmJsTest --tests "*StockLevelMathTest*"`
Expected: FAIL(`comfortableStock` 等が未定義)。

- [ ] **Step 3: 純関数を実装**

Create `StockLevelMath.kt`:
```kotlin
package net.brightroom.mindstock.frontend.designsystem.atom

import kotlin.math.max

/** 「余裕のある」基準量 = max(min*2, min+3, qty, 1)。core.jsx StockBar より。 */
fun comfortableStock(qty: Int, min: Int): Int = max(max(min * 2, min + 3), max(qty, 1))

fun fillFraction(qty: Int, min: Int): Float =
    (qty.toFloat() / comfortableStock(qty, min)).coerceIn(0f, 1f)

fun minFraction(qty: Int, min: Int): Float =
    (min.toFloat() / comfortableStock(qty, min)).coerceIn(0f, 1f)
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :frontend:wasmJsTest --tests "*StockLevelMathTest*"`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/StockLevelMath.kt \
        frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/StockLevelMathTest.kt
git commit -m "test(frontend): StockLevelBar 充填率の純関数を TDD で追加"
```

---

## Task 5: StockLevelBar を Canvas で忠実描画

**Files:**
- Modify: `frontend/src/commonMain/kotlin/.../designsystem/atom/StockLevelBar.kt`

トラック(surface2)+ fill(status色)+ min 閾値マーカー + 幅アニメ。core.jsx: height 8、radius 99(完全丸)、min マーカーは faint 2px。

- [ ] **Step 1: 差し替え実装**

`StockLevelBar.kt`:
```kotlin
package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens

@Composable
fun StockLevelBar(
    qty: Int,
    min: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMindstockTokens.current
    val track = MaterialTheme.colorScheme.surfaceVariant
    val target = fillFraction(qty, min)
    val minPos = minFraction(qty, min)
    val animated by animateFloatAsState(target, label = "stockFill")
    Canvas(modifier = modifier.fillMaxWidth().height(8.dp)) {
        val h = size.height
        val r = CornerRadius(h / 2, h / 2)
        // track
        drawRoundRect(color = track, size = Size(size.width, h), cornerRadius = r)
        // fill
        if (animated > 0f) {
            drawRoundRect(color = color, size = Size(size.width * animated, h), cornerRadius = r)
        }
        // min threshold marker(faint 縦線)
        val x = size.width * minPos
        drawRoundRect(
            color = tokens.faint.copy(alpha = 0.5f),
            topLeft = Offset(x - 1.dp.toPx(), -3.dp.toPx()),
            size = Size(2.dp.toPx(), h + 6.dp.toPx()),
            cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
        )
    }
}
```
（呼び出し側 `ProductCard` の `StockLevelBar(qty=..., min=..., color=...)` シグネチャ不変。)

- [ ] **Step 2: コンパイル + 既存テスト確認**

Run: `./gradlew :frontend:compileKotlinWasmJs && ./gradlew :frontend:wasmJsTest --tests "*StockLevelMathTest*"`
Expected: SUCCESSFUL / PASS。

- [ ] **Step 3: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/StockLevelBar.kt
git commit -m "feat(frontend): StockLevelBar を Canvas で忠実描画(min マーカー+アニメ)"
```

---

## Task 6: StatusDot(soft リング + ラベル)

**Files:**
- Modify: `frontend/src/commonMain/kotlin/.../designsystem/atom/StatusDot.kt`

core.jsx: 8px ドット + `box-shadow 0 0 0 3px soft`(soft リング)+ 任意ラベル(600/12.5 status色)。soft 色は呼び出し側から渡す(tokens の statusXxxSoft)。

- [ ] **Step 1: 差し替え実装**

`StatusDot.kt`:
```kotlin
package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType

/** ok/low/out のドット。color/soft は呼び出し側が MindstockTokens から渡す。 */
@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    soft: Color = Color.Unspecified,
    label: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Canvas(modifier = Modifier.size(14.dp)) {
            val c = size.minDimension / 2
            if (soft != Color.Unspecified) drawCircle(color = soft, radius = 7.dp.toPx(), center = center)
            drawCircle(color = color, radius = 4.dp.toPx(), center = center)
        }
        if (label != null) {
            androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
            AppText(text = label, style = MindstockType.statusLabel(), color = color)
        }
    }
}
```
注: 既存 `StatusDot(color = statusColor)` 呼び出し(ProductCard)は soft/label 省略で動く。Task 14 で soft/label を渡すよう更新する。

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/StatusDot.kt
git commit -m "feat(frontend): StatusDot に soft リングとラベルを追加"
```

---

## Task 7: Thumb(ハッチ背景)

**Files:**
- Modify: `frontend/src/commonMain/kotlin/.../designsystem/atom/Thumb.kt`

core.jsx: surface2 背景 + lineSoft 枠 + 45° ハッチ(accent 8%)+ 中央アイコン。画像があれば画像優先(P6-2 だが引数だけ用意)。

- [ ] **Step 1: 差し替え実装**

`Thumb.kt`:
```kotlin
package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 商品サムネ。画像表示は将来(P6-2)。現状はハッチ背景 + カテゴリアイコン。 */
@Composable
fun Thumb(
    icon: AppIconName = AppIconName.Box,
    size: Dp = 48.dp,
    radius: Dp = 14.dp,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val hatch = scheme.primary.copy(alpha = 0.08f)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .border(BorderStroke(1.dp, scheme.outlineVariant), RoundedCornerShape(radius))
            .drawBehind {
                drawRect(scheme.surfaceVariant)
                val step = 10.dp.toPx()
                val w = this.size.width
                val h = this.size.height
                var x = -h
                while (x < w) {
                    drawLine(
                        color = hatch,
                        start = Offset(x, h),
                        end = Offset(x + h, 0f),
                        strokeWidth = 5.dp.toPx(),
                    )
                    x += step
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(icon, contentDescription = null, tint = scheme.primary)
    }
}
```
注: `AppIcon` の `tint` 引数は Task 11 で追加。未追加なら一旦 tint 無しにし Task 11 後に戻す。

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/Thumb.kt
git commit -m "feat(frontend): Thumb にハッチ背景とアイコン引数を追加"
```

---

## Task 8: SegmentedControl / Stepper / RoundBtn の忠実化

**Files:**
- Modify: `designsystem/atom/SegmentedControl.kt` / `Stepper.kt` / `RoundBtn.kt`

- [ ] **Step 1: SegmentedControl をトラック+選択タブ surface に**

`SegmentedControl.kt`(`SingleChoiceSegmentedButtonRow` を独自実装に差し替え。core.jsx `Seg`: トラック surface2 + 角丸13 + padding3、選択タブ surface + 影 + 角丸10、高さ34、未選択 faint):
```kotlin
package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import net.brightroom.mindstock.frontend.designsystem.theme.ShadowLevel
import net.brightroom.mindstock.frontend.designsystem.theme.softShadow

data class SegOption(val key: String, val label: String)

@Composable
fun SegmentedControl(
    options: List<SegOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val tokens = LocalMindstockTokens.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(scheme.surfaceVariant)
            .padding(3.dp),
    ) {
        options.forEach { o ->
            val active = o.key == selectedKey
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .then(if (active) Modifier.softShadow(ShadowLevel.Sm, RoundedCornerShape(10.dp)) else Modifier)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) scheme.surface else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(o.key) },
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    text = o.label,
                    style = MindstockType.sectionMeta(),
                    color = if (active) scheme.onSurface else tokens.faint,
                )
            }
        }
    }
}
```

- [ ] **Step 2: Stepper の大数字化**

`Stepper.kt` の `Text(... headlineSmall)` を大数字に。最小差分で `AppText` + `MindstockType` に寄せる:
```kotlin
// import 追加: net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
// Text(...) を以下に差し替え
AppText(
    text = "$value$unit",
    style = MindstockType.bigQty(),
    modifier = Modifier.padding(horizontal = 8.dp),
)
```
（`androidx.compose.material3.Text` / `MaterialTheme` import が他で未使用になれば削除。）

- [ ] **Step 3: RoundBtn を 58dp・surface・枠線に**

`RoundBtn.kt`(core.jsx: 58、surface、line 枠、影sm。`FilledTonalIconButton` → `IconButton` + 自前装飾):
```kotlin
package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import net.brightroom.mindstock.frontend.designsystem.theme.ShadowLevel
import net.brightroom.mindstock.frontend.designsystem.theme.softShadow

@Composable
fun RoundBtn(
    icon: AppIconName,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(58.dp)
            .softShadow(ShadowLevel.Sm, CircleShape)
            .clip(CircleShape)
            .background(scheme.surface)
            .border(BorderStroke(1.dp, scheme.outline), CircleShape),
    ) {
        AppIcon(icon, contentDescription = contentDescription, tint = scheme.onSurface)
    }
}
```

- [ ] **Step 4: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/SegmentedControl.kt \
        frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/Stepper.kt \
        frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/RoundBtn.kt
git commit -m "feat(frontend): Seg/Stepper/RoundBtn をモック忠実に再装飾"
```

---

## Task 9: Sheet / Toast のトーン調整

**Files:**
- Modify: `designsystem/atom/Sheet.kt` / `Toast.kt`

- [ ] **Step 1: Sheet の container/角丸/タイトルを token に**

`Sheet.kt` の `ModalBottomSheet` に `containerColor = MaterialTheme.colorScheme.surface`、`shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)` を指定し、タイトルを `AppText(style = MindstockType.summaryTitle())` に差し替える:
```kotlin
// import: androidx.compose.foundation.shape.RoundedCornerShape, ...theme.MindstockType, atom.AppText
ModalBottomSheet(
    onDismissRequest = onClose,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp)) {
        AppText(text = title, style = MindstockType.summaryTitle(), modifier = Modifier.padding(bottom = 18.dp))
        content()
    }
}
```
（`material3.Text` import は AppText に置換で不要になれば削除。）

- [ ] **Step 2: Toast を ink 背景 + 角丸に**

`Toast.kt`(core.jsx Toast: ink 背景 + surface 文字 + 角丸16 + 影pop。Snackbar の色を上書き):
```kotlin
package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType

@Composable
fun Toast(
    message: String?,
    modifier: Modifier = Modifier,
) {
    if (message == null) return
    Snackbar(
        modifier = modifier.padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.onSurface,
        contentColor = MaterialTheme.colorScheme.surface,
    ) {
        AppText(text = message, style = MindstockType.summarySub(), color = MaterialTheme.colorScheme.surface)
    }
}
```

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/Sheet.kt \
        frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/Toast.kt
git commit -m "feat(frontend): Sheet/Toast のトーンをモックに合わせる"
```

---

## Task 10: AppIcon の glyph 拡張(重要分の ImageVector 移植)

**Files:**
- Modify: `frontend/src/commonMain/kotlin/.../designsystem/atom/AppIcon.kt`

ユーザ決定: ロゴ箱 + 商品カテゴリ(drop/paper/egg/bottle/salt/bolt/leaf)を ImageVector 移植、nav/汎用は material-icons-extended。`size`/`tint` 引数も足す(他 atom が使う)。

- [ ] **Step 1: AppIcon に size/tint を足し、AppIconName を拡張**

`AppIcon.kt` を差し替え。material 対応分を拡張し、`size`/`tint` 引数を追加:
```kotlin
package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class AppIconName {
    Box, Cart, Plus, Minus, Clock, Home, User, Bell, Search, ChevronRight, Trend,
    // 商品カテゴリ(ImageVector 移植)
    Drop, Paper, Egg, Bottle, Salt, Bolt, Leaf,
}

private fun AppIconName.vector(): ImageVector = when (this) {
    AppIconName.Box -> Icons.Filled.Inventory2
    AppIconName.Cart -> Icons.Filled.ShoppingCart
    AppIconName.Plus -> Icons.Filled.Add
    AppIconName.Minus -> Icons.Filled.Remove
    AppIconName.Clock -> Icons.Outlined.AccessTime
    AppIconName.Home -> Icons.Outlined.Home
    AppIconName.User -> Icons.Outlined.Person
    AppIconName.Bell -> Icons.Outlined.Notifications
    AppIconName.Search -> Icons.Outlined.Search
    AppIconName.ChevronRight -> Icons.Outlined.KeyboardArrowRight
    AppIconName.Trend -> Icons.Outlined.TrendingUp
    AppIconName.Drop -> MindstockGlyphs.Drop
    AppIconName.Paper -> MindstockGlyphs.Paper
    AppIconName.Egg -> MindstockGlyphs.Egg
    AppIconName.Bottle -> MindstockGlyphs.Bottle
    AppIconName.Salt -> MindstockGlyphs.Salt
    AppIconName.Bolt -> MindstockGlyphs.Bolt
    AppIconName.Leaf -> MindstockGlyphs.Leaf
}

@Composable
fun AppIcon(
    name: AppIconName,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    tint: Color = Color.Unspecified,
) {
    Icon(
        imageVector = name.vector(),
        contentDescription = contentDescription,
        modifier = modifier.then(androidx.compose.foundation.layout.SizeModifierStub(size)),
        tint = if (tint == Color.Unspecified) androidx.compose.material3.LocalContentColor.current else tint,
    )
}
```
注: `SizeModifierStub` は誤り。`modifier.size(size)` を使う(`import androidx.compose.foundation.layout.size`)。上の行を `modifier = modifier.size(size)` に直すこと。

- [ ] **Step 2: MindstockGlyphs を作る(core.jsx SVG パス → ImageVector)**

同ファイル末尾、または `AppIcon.kt` 隣に `MindstockGlyphs.kt` を作り、core.jsx の各 `path d="..."` を `ImageVector.Builder` で再現する。SVG は viewBox 0 0 24 24、stroke ベース(fill=none, stroke=currentColor, strokeWidth≈1.7)。Compose では `stroke = SolidColor(...)`、`strokeLineWidth`、`fill = null` で `addPath(PathParser(d).toNodes())` を使う。例(drop):
```kotlin
package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

object MindstockGlyphs {
    private fun stroke(name: String, pathData: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                fill = null,
                stroke = SolidColor(androidx.compose.ui.graphics.Color.Black), // tint は AppIcon 側で上書き
                strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathFillType = PathFillType.NonZero,
            )
        }.build()

    // core.jsx の各 path d をそのまま流用
    val Drop = stroke("Drop", "M12 3.5C12 3.5 5.5 10 5.5 14.5a6.5 6.5 0 0 0 13 0C18.5 10 12 3.5 12 3.5z")
    val Paper = stroke("Paper", "M12 3.5v5M12 15.5v5M3.5 12h5M15.5 12h5") // + circles は下記注参照
    val Egg = stroke("Egg", "M12 3c3.5 0 6 5 6 9a6 6 0 0 1-12 0c0-4 2.5-9 6-9z")
    val Bottle = stroke("Bottle", "M10 3h4v2.5l1.2 2.4a3 3 0 0 1 .3 1.3V19a2 2 0 0 1-2 2h-3a2 2 0 0 1-2-2V9.2a3 3 0 0 1 .3-1.3L10 5.5V3zM9 12.5h6")
    val Salt = stroke("Salt", "M8 9h8l-1 11H9L8 9zM9 9V6a3 3 0 0 1 6 0v3M11 4.5h2")
    val Bolt = stroke("Bolt", "M13 3L5 13h5l-1 8 8-10h-5l1-8z")
    val Leaf = stroke("Leaf", "M5 19C5 11 11 5 20 5c0 9-6 15-14 15a5 5 0 0 1-1-7M9 15c3-3 6-4 9-5")
}
```
注:
- `paper` は core.jsx で `circle` 2 つ + 十字線。`circle` は path に無いので、`paper` は近似として上記十字のみ、または `Icons.Outlined.Description` 等の material で代替してよい(忠実度の優先度低)。実装者判断で material 代替可。
- `box` ロゴ(`M3 8l9-5 9 5v8l-9 5-9-5V8z` 他)も同様に `stroke("Box", ...)` で足してよいが、既存 `Icons.Filled.Inventory2` のままでも可(ロゴはアプリ shell では未露出)。商品アイコンを優先。
- `PathParser().parsePathString(d).toNodes()` の API は Compose のバージョンで `parsePathString` 名が異なることがある。コンパイルエラー時は `addPathNodes(d)`(`androidx.compose.ui.graphics.vector.addPathNodes`)を使う。

- [ ] **Step 3: コンパイル確認 + 既存呼び出しの修正**

`AppIcon` のシグネチャ変更(size/tint 追加・デフォルトあり)は後方互換。`AppShell.kt` の `AppIcon(tab.icon, contentDescription = ...)` はそのまま動く。
Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL（`size`/`parsePathString` のエラーが出たら注の通り修正）。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/AppIcon.kt \
        frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/MindstockGlyphs.kt
git commit -m "feat(frontend): AppIcon に size/tint と商品カテゴリ glyph(ImageVector)を追加"
```

---

## Task 11: SearchField / HouseholdPill / AddTile(新規 atom)

**Files:**
- Create: `designsystem/atom/SearchField.kt` / `HouseholdPill.kt` / `AddTile.kt`

- [ ] **Step 1: SearchField**

core.jsx: 高さ50・角丸14・surface・検索アイコン左・クリア右・focus 時 accent 枠。
```kotlin
package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val tokens = LocalMindstockTokens.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val active = focused || value.isNotEmpty()
    val borderColor = if (active) scheme.primary else scheme.outline
    Row(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surface)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppIcon(AppIconName.Search, contentDescription = null, size = 19.dp, tint = if (active) scheme.primary else tokens.faint)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            interactionSource = interaction,
            singleLine = true,
            textStyle = MindstockType.button().copy(color = scheme.onSurface),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(scheme.primary),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (value.isEmpty()) AppText(placeholder, style = MindstockType.button(), color = tokens.faint)
                inner()
            },
        )
        if (value.isNotEmpty()) {
            AppIcon(AppIconName.Plus, contentDescription = "clear", size = 16.dp, tint = tokens.faint,
                modifier = Modifier.graphicsClear(onValueChange))
        }
    }
}

// クリアボタンの簡易クリック(Plus を 45° 回せば x だが、まずは clickable のみ)
private fun Modifier.graphicsClear(onClear: (String) -> Unit): Modifier =
    this.then(androidx.compose.foundation.clickable(onClick = { onClear("") }, indication = null,
        interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource()) as Modifier)
```
注: `graphicsClear` は読みづらいので、実装者は素直に別 `Box{...}.clickable{ onValueChange("") }` に展開してよい。x アイコンは `AppIconName` に `Close`(`Icons.Outlined.Close`)を足して使うのが綺麗(Task 10 の enum に `Close` を追加し material 対応)。

- [ ] **Step 2: HouseholdPill**

core.jsx `HouseholdPill`: home アイコン丸 + 世帯名 + メンバー数、surface2、角丸、タップ可。本パスは onClick だけ受けて表示。
```kotlin
package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType

@Composable
fun HouseholdPill(
    name: String,
    memberCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surface)
            .border(1.dp, scheme.outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(scheme.primaryContainer), contentAlignment = Alignment.Center) {
            AppIcon(AppIconName.Home, contentDescription = null, size = 17.dp, tint = scheme.primary)
        }
        Column {
            AppText(name, style = MindstockType.sectionMeta(), color = scheme.onSurface)
            AppText("$memberCount 人", style = MindstockType.greeting(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
```

- [ ] **Step 3: AddTile**

core.jsx: 高さ58・角丸lg・破線枠・plus + ラベル。Compose の破線は `PathEffect.dashPathEffect` を `drawBehind` で。
```kotlin
package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType

@Composable
fun AddTile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .drawBehind {
                drawRoundRect(
                    color = scheme.outline,
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx()),
                )
            }
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        AppIcon(AppIconName.Plus, contentDescription = null, size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        AppText(label, style = MindstockType.sectionMeta(), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
```

- [ ] **Step 4: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/SearchField.kt \
        frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/HouseholdPill.kt \
        frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/AddTile.kt
git commit -m "feat(frontend): SearchField/HouseholdPill/AddTile atom を追加"
```

---

## Task 12: 商品アイコンのヒューリスティック(純関数 + TDD)

**Files:**
- Create: `feature/inventory/ProductGlyph.kt`
- Create: `commonTest/.../feature/inventory/ProductGlyphTest.kt`

`Product` は category/icon を持たないため、名前から `AppIconName` を推定する(既定 Box)。

- [ ] **Step 1: 失敗するテスト**

`ProductGlyphTest.kt`:
```kotlin
package net.brightroom.mindstock.frontend.feature.inventory

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName

class ProductGlyphTest {
    @Test fun soap_maps_to_drop() { glyphForProductName("キレイキレイ 泡ハンドソープ") shouldBe AppIconName.Drop }
    @Test fun paper_maps_to_paper() { glyphForProductName("トイレットペーパー 12ロール") shouldBe AppIconName.Paper }
    @Test fun egg_maps_to_egg() { glyphForProductName("卵 10個入り") shouldBe AppIconName.Egg }
    @Test fun battery_maps_to_bolt() { glyphForProductName("単3 アルカリ乾電池") shouldBe AppIconName.Bolt }
    @Test fun unknown_maps_to_box() { glyphForProductName("謎の商品") shouldBe AppIconName.Box }
}
```

- [ ] **Step 2: 落ちることを確認**

Run: `./gradlew :frontend:wasmJsTest --tests "*ProductGlyphTest*"`
Expected: FAIL。

- [ ] **Step 3: 実装**

`ProductGlyph.kt`:
```kotlin
package net.brightroom.mindstock.frontend.feature.inventory

import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName

/** 商品名から表示アイコンを推定(暫定。将来 domain がカテゴリを持てば差し替え)。 */
fun glyphForProductName(name: String): AppIconName = when {
    listOf("ソープ", "シャンプー", "ハンドクリーム", "洗剤").any { it in name } -> AppIconName.Drop
    listOf("ペーパー", "ティッシュ", "トイレット").any { it in name } -> AppIconName.Paper
    listOf("卵", "たまご").any { it in name } -> AppIconName.Egg
    listOf("牛乳", "ミルク", "ジュース", "茶", "水", "ボトル").any { it in name } -> AppIconName.Bottle
    listOf("醤油", "塩", "マヨネーズ", "調味").any { it in name } -> AppIconName.Salt
    listOf("電池", "バッテリ").any { it in name } -> AppIconName.Bolt
    else -> AppIconName.Box
}
```

- [ ] **Step 4: 通ることを確認**

Run: `./gradlew :frontend:wasmJsTest --tests "*ProductGlyphTest*"`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ProductGlyph.kt \
        frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ProductGlyphTest.kt
git commit -m "test(frontend): 商品名→アイコン推定の純関数を TDD で追加"
```

---

## Task 13: StockSummary 集計(純関数 + TDD)+ SummaryStrip

**Files:**
- Create: `feature/inventory/StockSummary.kt`
- Create: `commonTest/.../feature/inventory/StockSummaryTest.kt`
- Create: `feature/inventory/ui/SummaryStrip.kt`

切らし/残りわずか件数は実値、予測/wanted は本パス非表示。

- [ ] **Step 1: 失敗するテスト**

`StockSummaryTest.kt`(`Stocks`/`Stock` の生成は既存テストのファクトリを参照。無ければ domain の `Stock(product, movements)` を直接組む。最小で件数のみ検証):
```kotlin
package net.brightroom.mindstock.frontend.feature.inventory

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import net.brightroom.mindstock.domain.model.inventory.stock.StockStatus

class StockSummaryTest {
    @Test
    fun counts_out_and_low_from_statuses() {
        val summary = stockSummaryOf(listOf(StockStatus.在庫切れ, StockStatus.在庫切れ, StockStatus.残りわずか, StockStatus.十分))
        summary.outCount shouldBe 2
        summary.lowCount shouldBe 1
        summary.needCount shouldBe 3
    }

    @Test
    fun all_sufficient_has_zero_need() {
        val summary = stockSummaryOf(listOf(StockStatus.十分, StockStatus.十分))
        summary.needCount shouldBe 0
    }
}
```
注: `StockStatus` の enum 名(`在庫切れ`/`残りわずか`/`十分`)は `domain/.../StockStatus.kt` を確認して合わせる。

- [ ] **Step 2: 落ちることを確認**

Run: `./gradlew :frontend:wasmJsTest --tests "*StockSummaryTest*"`
Expected: FAIL。

- [ ] **Step 3: 実装**

`StockSummary.kt`:
```kotlin
package net.brightroom.mindstock.frontend.feature.inventory

import net.brightroom.mindstock.domain.model.inventory.stock.StockStatus

/** 在庫サマリ(買い物 CTA 用)。予測日数/wanted は P6-1b まで未対応。 */
data class StockSummary(val outCount: Int, val lowCount: Int) {
    val needCount: Int get() = outCount + lowCount
}

fun stockSummaryOf(statuses: List<StockStatus>): StockSummary =
    StockSummary(
        outCount = statuses.count { it == StockStatus.在庫切れ },
        lowCount = statuses.count { it == StockStatus.残りわずか },
    )
```

- [ ] **Step 4: 通ることを確認**

Run: `./gradlew :frontend:wasmJsTest --tests "*StockSummaryTest*"`
Expected: PASS。

- [ ] **Step 5: SummaryStrip を実装**

`ui/SummaryStrip.kt`(core.jsx `SummaryStrip`: need あれば accent カード、無ければ surface。アイコン丸 + タイトル + サブ + chevron。予測ストリップは本パスでは省略 or 「補充は不要」テキストのみ):
```kotlin
package net.brightroom.mindstock.frontend.feature.inventory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import net.brightroom.mindstock.frontend.designsystem.theme.ShadowLevel
import net.brightroom.mindstock.frontend.designsystem.theme.softShadow
import net.brightroom.mindstock.frontend.feature.inventory.StockSummary

@Composable
fun SummaryStrip(
    summary: StockSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val need = summary.needCount > 0
    val shape = RoundedCornerShape(22.dp)
    val container = if (need) scheme.primary else scheme.surface
    val onContainer = if (need) scheme.onPrimary else scheme.onSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .softShadow(if (need) ShadowLevel.Md else ShadowLevel.Sm, shape)
            .clip(shape)
            .background(container)
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(13.dp))
                .background(if (need) scheme.onPrimary.copy(alpha = 0.18f) else scheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(AppIconName.Cart, contentDescription = null, size = 24.dp, tint = if (need) scheme.onPrimary else scheme.primary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AppText(
                if (need) "${summary.needCount}点 買い物リストにあります" else "在庫はぜんぶ足りています",
                style = MindstockType.summaryTitle(), color = onContainer,
            )
            AppText(
                if (need) "切らし ${summary.outCount} ・ そろそろ ${summary.lowCount}" else "いまのところ補充は不要です",
                style = MindstockType.summarySub(), color = onContainer.copy(alpha = 0.82f),
            )
        }
        AppIcon(AppIconName.ChevronRight, contentDescription = null, size = 20.dp, tint = onContainer.copy(alpha = 0.7f))
    }
}
```
注: SummaryStrip の文言は本来 strings.xml(Task 15 で追加)。ここでは可読性のため literal を置いているが、**Task 15 で `stringResource` 化する**(i18n 規約)。実装者は Task 15 とまとめて文言を resource 化してよい。

- [ ] **Step 6: コンパイル + テスト確認**

Run: `./gradlew :frontend:compileKotlinWasmJs && ./gradlew :frontend:wasmJsTest --tests "*StockSummaryTest*"`
Expected: SUCCESSFUL / PASS。

- [ ] **Step 7: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/StockSummary.kt \
        frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/inventory/StockSummaryTest.kt \
        frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/SummaryStrip.kt
git commit -m "feat(frontend): StockSummary 集計と SummaryStrip を追加"
```

---

## Task 14: ProductCard restyle + CompactCard 追加

**Files:**
- Modify: `feature/inventory/ui/ProductCard.kt`
- Create: `feature/inventory/ui/CompactCard.kt`

core.jsx `ProductCard`(list): surface カード + lineSoft 枠 + 影sm + 角丸lg + padding18。Thumb48 + 名前(cardTitle)+ StatusDot(withLabel)+ 右側に大数字+単位 + StockBar + 補充(soft)/消費(ghost)。

- [ ] **Step 1: ProductCard を差し替え**

`ProductCard.kt`:
```kotlin
package net.brightroom.mindstock.frontend.feature.inventory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.action_consume
import mindstock.frontend.generated.resources.action_replenish
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.StockStatus
import net.brightroom.mindstock.frontend.designsystem.atom.*
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import net.brightroom.mindstock.frontend.designsystem.theme.ShadowLevel
import net.brightroom.mindstock.frontend.designsystem.theme.softShadow
import net.brightroom.mindstock.frontend.feature.inventory.glyphForProductName
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProductCard(
    stock: Stock,
    onOpen: (Stock) -> Unit,
    onReplenish: (Stock) -> Unit,
    onConsume: (Stock) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val tokens = LocalMindstockTokens.current
    val (statusColor, statusSoft, statusLabel) = when (stock.status()) {
        StockStatus.在庫切れ -> Triple(tokens.statusOut, tokens.statusOutSoft, "切らし中")
        StockStatus.残りわずか -> Triple(tokens.statusLow, tokens.statusLowSoft, "そろそろ")
        StockStatus.十分 -> Triple(tokens.statusOk, tokens.statusOkSoft, "十分")
    }
    val shape = RoundedCornerShape(22.dp)
    val qty = stock.currentQuantity()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .softShadow(ShadowLevel.Sm, shape)
            .clip(shape)
            .background(scheme.surface)
            .border(1.dp, scheme.outlineVariant, shape)
            .clickable { onOpen(stock) }
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            Thumb(icon = glyphForProductName(stock.product.name()), size = 48.dp)
            Column(Modifier.weight(1f)) {
                AppText(stock.product.name(), style = MindstockType.cardTitle(), color = scheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                StatusDot(color = statusColor, soft = statusSoft, label = statusLabel)
            }
            Column(horizontalAlignment = Alignment.End) {
                AppText("$qty", style = MindstockType.bigQty(),
                    color = if (stock.status() == StockStatus.在庫切れ) tokens.statusOut else scheme.onSurface)
                AppText(stock.product.setting.unit(), style = MindstockType.unitCaption(), color = tokens.faint)
            }
        }
        StockLevelBar(qty = qty, min = stock.product.setting.minimumStock(), color = statusColor)
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            AppButton(onClick = { onReplenish(stock) }, variant = ButtonVariant.Soft, size = ButtonSize.Sm,
                icon = AppIconName.Plus, modifier = Modifier.weight(1f)) {
                AppText(stringResource(Res.string.action_replenish))
            }
            AppButton(onClick = { onConsume(stock) }, variant = ButtonVariant.Ghost, size = ButtonSize.Sm,
                icon = AppIconName.Minus, modifier = Modifier.weight(1f)) {
                AppText(stringResource(Res.string.action_consume))
            }
        }
    }
}
```
注: `statusLabel` の文言("切らし中"等)は strings.xml 化を推奨(Task 15 でまとめて)。

- [ ] **Step 2: CompactCard(grid 用)を作る**

`ui/CompactCard.kt`(core.jsx `CompactCard`: padding14・角丸lg・Thumb40 + 右上に数量 + 名前2行 + StatusDot + StockBar + アイコンのみボタン):
```kotlin
package net.brightroom.mindstock.frontend.feature.inventory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.StockStatus
import net.brightroom.mindstock.frontend.designsystem.atom.*
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import net.brightroom.mindstock.frontend.designsystem.theme.ShadowLevel
import net.brightroom.mindstock.frontend.designsystem.theme.softShadow
import net.brightroom.mindstock.frontend.feature.inventory.glyphForProductName

@Composable
fun CompactCard(
    stock: Stock,
    onOpen: (Stock) -> Unit,
    onReplenish: (Stock) -> Unit,
    onConsume: (Stock) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val tokens = LocalMindstockTokens.current
    val (statusColor, statusSoft, statusLabel) = when (stock.status()) {
        StockStatus.在庫切れ -> Triple(tokens.statusOut, tokens.statusOutSoft, "切らし中")
        StockStatus.残りわずか -> Triple(tokens.statusLow, tokens.statusLowSoft, "そろそろ")
        StockStatus.十分 -> Triple(tokens.statusOk, tokens.statusOkSoft, "十分")
    }
    val shape = RoundedCornerShape(22.dp)
    val qty = stock.currentQuantity()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .softShadow(ShadowLevel.Sm, shape)
            .clip(shape)
            .background(scheme.surface)
            .border(1.dp, scheme.outlineVariant, shape)
            .clickable { onOpen(stock) }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Thumb(icon = glyphForProductName(stock.product.name()), size = 40.dp, radius = 12.dp)
            Row(verticalAlignment = Alignment.Bottom) {
                AppText("$qty", style = MindstockType.bigQty(),
                    color = if (stock.status() == StockStatus.在庫切れ) tokens.statusOut else scheme.onSurface)
                AppText(stock.product.setting.unit(), style = MindstockType.unitCaption(), color = tokens.faint,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
            }
        }
        AppText(stock.product.name(), style = MindstockType.cardTitle(), color = scheme.onSurface,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(37.dp))
        StatusDot(color = statusColor, soft = statusSoft, label = statusLabel)
        StockLevelBar(qty = qty, min = stock.product.setting.minimumStock(), color = statusColor)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            AppButton(onClick = { onReplenish(stock) }, variant = ButtonVariant.Soft, size = ButtonSize.Sm,
                icon = AppIconName.Plus, modifier = Modifier.weight(1f)) {}
            AppButton(onClick = { onConsume(stock) }, variant = ButtonVariant.Ghost, size = ButtonSize.Sm,
                icon = AppIconName.Minus, modifier = Modifier.weight(1f)) {}
        }
    }
}
```

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/ProductCard.kt \
        frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/CompactCard.kt
git commit -m "feat(frontend): ProductCard restyle と CompactCard(grid)を追加"
```

---

## Task 15: 文言追加 + StockHomeScreen 再構成

**Files:**
- Modify: `frontend/src/commonMain/composeResources/values/strings.xml`
- Modify: `feature/inventory/ui/StockHomeScreen.kt`
- Modify: `feature/inventory/ui/SummaryStrip.kt` / `ProductCard.kt` / `CompactCard.kt`(literal を stringResource 化)

- [ ] **Step 1: strings.xml に追加**

`<resources>` 内に追加:
```xml
<string name="status_ok">十分</string>
<string name="status_low">そろそろ</string>
<string name="status_out">切らし中</string>
<string name="stock_count_all">すべて · %1$d点</string>
<string name="stock_search_count">%1$d件 見つかりました</string>
<string name="summary_need_title">%1$d点 買い物リストにあります</string>
<string name="summary_need_sub">切らし %1$d ・ そろそろ %2$d</string>
<string name="summary_ok_title">在庫はぜんぶ足りています</string>
<string name="summary_ok_sub">いまのところ補充は不要です</string>
<string name="household_member_count">%1$d 人</string>
<string name="household_default_name">わたしの家</string>
```

- [ ] **Step 2: SummaryStrip / ProductCard / CompactCard の literal を stringResource 化**

各ファイルの "切らし中" 等・SummaryStrip タイトル/サブを `stringResource(Res.string.status_xxx)` / `stringResource(Res.string.summary_xxx, ...)` に置換する(i18n 規約)。`statusLabel` は `when` 内で `stringResource` を呼ぶ(Composable 内なので可)。

- [ ] **Step 3: StockHomeScreen を再構成**

`StockHomeScreen.kt` の `InventoryUiState.Content` ブロックを差し替え。ヘッダ chrome(HouseholdPill + ベル)→ 挨拶 + 見出し → SummaryStrip → SearchField → 件数 + Seg → grid/list → AddTile。`displayName` は既存引数。世帯名/メンバー数は本パスではダミー(引数が無いので暫定文言)。
```kotlin
is InventoryUiState.Content -> {
    // ヘッダ chrome
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        // 世帯名/人数は P6-1b で session から渡す。本パスは strings の既定文言で表示のみ。
        HouseholdPill(name = stringResource(Res.string.household_default_name), memberCount = 1, onClick = {})
        RoundBtn(icon = AppIconName.Bell, contentDescription = "notifications", onClick = {})
    }
    AppText(stringResource(Res.string.stock_greeting, displayName), style = MindstockType.greeting(),
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    AppText(stringResource(Res.string.stock_title), style = MindstockType.screenTitle(), color = MaterialTheme.colorScheme.onSurface)

    val visible = state.visibleStocks()
    val summary = stockSummaryOf(state.stocks.list.map { it.status() })
    if (state.query.isBlank()) SummaryStrip(summary = summary, onClick = {})

    SearchField(value = state.query, onValueChange = onQueryChange,
        placeholder = stringResource(Res.string.stock_search_placeholder), modifier = Modifier.fillMaxWidth())

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        AppText(
            if (state.query.isNotBlank()) stringResource(Res.string.stock_search_count, visible.list.size)
            else stringResource(Res.string.stock_count_all, state.stocks.list.size),
            style = MindstockType.sectionMeta(), color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SegmentedControl(
            options = listOf(
                SegOption(StockView.List.name, stringResource(Res.string.stock_view_list)),
                SegOption(StockView.Grid.name, stringResource(Res.string.stock_view_grid)),
            ),
            selectedKey = state.view.name,
            onSelect = { onSelectView(StockView.valueOf(it)) },
            modifier = Modifier.width(110.dp),
        )
    }

    if (state.query.isNotBlank() && visible.list.isEmpty()) {
        AppText(stringResource(Res.string.stock_search_empty, state.query.trim()),
            style = MindstockType.cardTitle(), color = MaterialTheme.colorScheme.onSurface)
    } else if (state.view == StockView.Grid) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2), modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(13.dp), horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            items(visible.list) { stock ->
                CompactCard(stock = stock, onOpen = onOpen, onReplenish = onReplenish, onConsume = onConsume)
            }
            if (state.query.isBlank()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    AddTile(label = stringResource(Res.string.stock_add_product), onClick = onAddProduct)
                }
            }
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            items(visible.list) { stock ->
                ProductCard(stock = stock, onOpen = onOpen, onReplenish = onReplenish, onConsume = onConsume)
            }
            if (state.query.isBlank()) {
                item { AddTile(label = stringResource(Res.string.stock_add_product), onClick = onAddProduct) }
            }
        }
    }
}
```
import 追加: `HouseholdPill`/`RoundBtn`/`SearchField`/`AddTile`/`AppIconName`/`MaterialTheme`/`MindstockType`/`SummaryStrip`/`CompactCard`/`stockSummaryOf`/`Row`/`width`/`weight`/`Alignment`/`GridItemSpan`。`PrimaryButton` の旧「商品を追加」行は AddTile に置換するので削除。
注: `MaterialTheme` を feature で使うのは designsystem 規約に触れる懸念があるが、`MaterialTheme.colorScheme` の色参照は既存 ProductCard でも `LocalMindstockTokens` 併用で行われている。色は `LocalMindstockTokens` か、designsystem に薄い `LocalAppColors` を足して参照するのが理想。**実装時、feature からの `MaterialTheme` 直参照を避けるため、必要な色(onSurface/onSurfaceVariant/primary)を `MindstockType` と同様に designsystem の関数で包むか、atom 側に閉じ込める**。最小では `LocalMindstockTokens` に `ink`/`sub` を足して feature はそれを使う。→ レビュー時に確認。

- [ ] **Step 4: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: 全テスト確認**

Run: `./gradlew :frontend:wasmJsTest`
Expected: 既存 + 新規テスト全 PASS。

- [ ] **Step 6: Commit**

```bash
git add frontend/src/commonMain/composeResources/values/strings.xml \
        frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/
git commit -m "feat(frontend): StockHome をモック忠実に再構成(ヘッダ/SummaryStrip/件数+Seg/grid・list/AddTile)"
```

---

## Task 16: 視覚検証ループ(モック横並び)→ 是正

**Files:** 是正に応じて Task 1〜15 のファイル

- [ ] **Step 1: dev server 起動 + スクショ(Task 0.5 のプレビュー経路で)**

Task 0.5 で用意したサンプルデータ経路(`previewStocks()` を `loadStocks` に注入)で dev server を起動し、
webapp-testing スキルで在庫画面(list / grid 両方)のスクショを撮る。認証/backend は不要。

- [ ] **Step 2: モックと横並び比較**

`/tmp/mindstock_mock/app/screens-a.jsx` の StockHome と並べ、以下を照合:
- 見出し「在庫」のウェイト/サイズ(800/25)、挨拶の faint トーン
- SummaryStrip の accent 配色・角丸・影・アイコン丸
- ProductCard の枠/影/角丸/padding18、大数字、StatusDot ラベル、StockBar の min マーカー、補充(soft)/消費(ghost)の差
- SearchField の高さ50/角丸14、Seg の選択タブ surface+影
- AddTile の破線
- 余白(card 間 13dp、セクション間)

- [ ] **Step 3: ズレを是正**

差分(色味・余白・ウェイト・影の強さ)を該当 atom / 画面で修正し、再スクショ。**「実装した」ではなく「モックと並べて一致を確認した」まで**到達する。

- [ ] **Step 4: Commit(是正があれば)**

```bash
git add -A
git commit -m "fix(frontend): モック横並び比較での見た目ズレを是正"
```

---

## Self-Review(プラン作成者チェック済み)

- **Spec coverage:** ①Token=Task1 / タイポ=Task2 / ②Atom 各=Task3,5,6,7,8,9,10,11 / AppIcon glyph=Task10 / ③StockHome=Task13,14,15 / SummaryStrip データ暫定=Task13 / アイコンヒューリスティック=Task12 / ④検証ループ=Task0,0.5,16(サンプル経路を必須化)/ ⑤テスト規約=Task4,12,13(kotlin.test+Kotest)/ 文言=Task15 / feature の material3 直参照回避=Task1(token 対応表)。全 spec 項目に対応タスクあり。
- **Loading/Error:** spec「軽量で可」に従い本パスでは現行の `AppText` 表示のまま(モックトーンへの寄せは最小)。意図的に深追いしない。
- **Placeholder scan:** TODO は Task15 の「世帯名/人数を session から(P6-1b)」のみ。これは spec で「ヘッダの世帯ピルは表示のみ・タップ/実データは後続」と明示済みの意図的な暫定。他に未確定コードなし。
- **Type consistency:** `comfortableStock/fillFraction/minFraction`(Task4→5)、`AppButton(variant,size,icon)`/`ButtonVariant`/`ButtonSize`(Task3→14)、`StatusDot(color,soft,label)`(Task6→14)、`Thumb(icon,size,radius)`(Task7→14)、`AppIcon(size,tint)`/`AppIconName` 拡張(Task10→7,11,13,14,15)、`stockSummaryOf`/`StockSummary`(Task13→15)、`glyphForProductName`(Task12→14)、`softShadow/ShadowLevel`(Task1→各)、`MindstockType.*`(Task2→各)整合。
- **既知の確認事項(実装時にレビュー):** (a) `PathParser().parsePathString` vs `addPathNodes` の API 名(Task10 Step2 注)。(b) feature からの `MaterialTheme` 直参照を designsystem 規約に沿って閉じ込めるか(Task15 Step3 注)— `LocalMindstockTokens` に `ink/sub` 追加が無難。(c) `StockStatus` enum 名の最終確認(Task13)。(d) `InventoryUiState.Content` に `query`/`visibleStocks()` が既にある前提(現行コードで確認済み)。
