# P6-4a レスポンシブ・アプリ chrome ＋ ウェルカム splash 実装計画

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** モック（`docs/ref/mindstock.zip`）に対する2つの構造的欠落 — サインイン splash 欠落とデスクトップ左サイドバー欠落 — を埋め、幅でモバイル(浮遊ボトムナビ)/デスクトップ(248dp サイドバー)を切り替える。

**Architecture:** `app/shell/AppShell` を幅分岐の dispatcher 化し、`shellKindFor(widthDp)` 純関数 + `currentWindowAdaptiveInfo()` で `WideShell`（新規サイドバー）/ Compact 経路（現行ボトムナビ）を選ぶ。認証は `AuthState.Unauthenticated` を新設し、`boot()` の `token==null` を「即 redirect」→「Unauthenticated に倒す」へ変更、新 `WelcomeScreen` を表示してボタンで Zitadel へ。

**Tech Stack:** Kotlin Multiplatform / Compose Multiplatform (Kotlin/Wasm) / Material3 Expressive / compose-adaptive (`currentWindowAdaptiveInfo`) / kotlin.test + Kotest assertions。

**設計根拠:** `docs/superpowers/specs/2026-06-07-p6-4a-responsive-shell-welcome-design.md`

---

## 前提・規約（着手前に必読）

- **層**: `app/shell/` はアプリ外枠なので adaptive API / material3 を直接使ってよい（`frontend-designsystem.md`）。feature 層ではないので atom 縛りは緩い。ただし配色・半径は `LocalMindstockTokens` を使う。
- **i18n**: ユーザ向け文言は `commonMain/composeResources/values/strings.xml`(ja) に置き `stringResource(Res.string.xxx)` 参照（`frontend-i18n-and-font.md`）。
- **テスト**: commonTest は `kotlin.test.@Test` + Kotest assertions（FunSpec 不可）。UI 描画網羅は追わない（`frontend-compose-conventions.md` / `testing.md`）。テストするのは `shellKindFor`（判定）と `boot()` 分岐（状態遷移）のみ。
- **検証コマンド**:
  - コンパイル: `./gradlew :frontend:compileKotlinWasmJs`（フルビルドは OOM するので使わない）
  - テストコンパイル: `./gradlew :frontend:compileTestKotlinWasmJs`
  - テスト実行: `./gradlew :frontend:wasmJsTest`（重い/不安定なら最低限テストコンパイルが通ることを必須ゲートとする）
- **重要な単純化**: `AppShell` は `AuthState.Ready` かつ `householdId != null` のときだけ描画される（`NeedHousehold` は shell 外の専用全画面）。よって shell に `hasHousehold` 概念は不要 = サイドバーは常に世帯ありの形で描く。

---

## ファイル構成

- **Create** `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/shell/ShellKind.kt` — 幅→ShellKind 純関数。
- **Create** `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/app/shell/ShellKindTest.kt` — 上記のテスト。
- **Modify** `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/auth/AuthState.kt` — `Unauthenticated` 追加。
- **Modify** `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/AuthViewModel.kt:83-87` — `token==null` を `Unauthenticated` に倒す。
- **Modify** `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/app/AuthViewModelTest.kt:100-108` — 既存「no_token→redirect」を「no_token→Unauthenticated」に置換。
- **Modify** `frontend/src/commonMain/composeResources/values/strings.xml` — welcome_* / sidebar_* キー追加。
- **Create** `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/welcome/WelcomeScreen.kt` — モック Login 準拠 splash。
- **Create** `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/shell/WideShell.kt` — 248dp サイドバー shell。
- **Modify** `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/shell/AppShell.kt` — 分岐 dispatcher 化 + 引数追加 + content 中央寄せ土台。
- **Modify** `frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/App.kt` — `Unauthenticated` 分岐に `WelcomeScreen` 配線 + `AppShell` 呼び出しに新引数。

---

## Task 1: `shellKindFor` 純関数 + テスト

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/shell/ShellKind.kt`
- Test: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/app/shell/ShellKindTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

`frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/app/shell/ShellKindTest.kt`:

```kotlin
package net.brightroom.mindstock.frontend.app.shell

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ShellKindTest {
    @Test
    fun below_840_is_compact() {
        shellKindFor(839) shouldBe ShellKind.Compact
    }

    @Test
    fun exactly_840_is_wide() {
        shellKindFor(840) shouldBe ShellKind.Wide
    }

    @Test
    fun large_width_is_wide() {
        shellKindFor(1280) shouldBe ShellKind.Wide
    }

    @Test
    fun tablet_portrait_stays_compact() {
        shellKindFor(700) shouldBe ShellKind.Compact
    }
}
```

- [ ] **Step 2: テストがコンパイル/実行で失敗することを確認**

Run: `./gradlew :frontend:compileTestKotlinWasmJs`
Expected: FAIL（`shellKindFor` / `ShellKind` 未定義）。

- [ ] **Step 3: 最小実装**

`frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/shell/ShellKind.kt`:

```kotlin
package net.brightroom.mindstock.frontend.app.shell

/** アプリ外枠の幅レイアウト種別。 */
enum class ShellKind {
    Compact,
    Wide,
}

/** Material3 Expanded 標準閾値。840dp 以上でデスクトップ・サイドバー、それ未満で浮遊ボトムナビ。 */
fun shellKindFor(widthDp: Int): ShellKind = if (widthDp >= 840) ShellKind.Wide else ShellKind.Compact
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :frontend:wasmJsTest --tests "*ShellKindTest*"`（重ければ `./gradlew :frontend:compileTestKotlinWasmJs` で代替ゲート）
Expected: PASS。

- [ ] **Step 5: コミット**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/shell/ShellKind.kt \
        frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/app/shell/ShellKindTest.kt
git commit -m "feat(frontend): shellKindFor で幅→shell 種別を判定する純関数を追加"
```

---

## Task 2: `AuthState.Unauthenticated` + boot 分岐変更

**Files:**
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/auth/AuthState.kt`
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/AuthViewModel.kt:83-87`
- Test: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/app/AuthViewModelTest.kt:100-108`

- [ ] **Step 1: 既存テストを新挙動に置換（失敗させる）**

`AuthViewModelTest.kt` の `no_token_redirects_to_authorize_and_stays_booting`（100-108 行）を丸ごと次に置換:

```kotlin
    @Test
    fun no_token_becomes_unauthenticated() =
        runTest {
            val deps = FakeAuthDeps(path = "/", token = null)
            val vm = AuthViewModel(deps)
            vm.boot()
            deps.redirectCalled shouldBe false
            vm.state.value.shouldBeInstanceOf<AuthState.Unauthenticated>()
        }
```

- [ ] **Step 2: 失敗を確認**

Run: `./gradlew :frontend:compileTestKotlinWasmJs`
Expected: FAIL（`AuthState.Unauthenticated` 未定義）。

- [ ] **Step 3: `AuthState` に `Unauthenticated` を追加**

`core/auth/AuthState.kt` の `Booting` data object の直後に追加:

```kotlin
    /** 未認証(有効 token 無し)。ウェルカム/サインイン splash を表示する。 */
    data object Unauthenticated : AuthState
```

- [ ] **Step 4: `boot()` の `token==null` 分岐を変更**

`app/AuthViewModel.kt` の以下（83-87 行付近）:

```kotlin
        val token = deps.loadValidToken()
        if (token == null) {
            deps.redirectToAuthorize()
            return // redirect でページ離脱。Booting のまま
        }
```

を次に変更:

```kotlin
        val token = deps.loadValidToken()
        if (token == null) {
            _state.value = AuthState.Unauthenticated
            return // ウェルカム splash を表示。ボタン押下で authorize へ。
        }
```

- [ ] **Step 5: テストが通ることを確認**

Run: `./gradlew :frontend:wasmJsTest --tests "*AuthViewModelTest*"`（重ければ `compileTestKotlinWasmJs`）
Expected: PASS（他の boot テストも緑のまま）。

- [ ] **Step 6: コミット**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/auth/AuthState.kt \
        frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/AuthViewModel.kt \
        frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/app/AuthViewModelTest.kt
git commit -m "feat(frontend): 未認証を AuthState.Unauthenticated で表し無言 redirect を廃止"
```

---

## Task 3: ウェルカム文言を strings.xml に追加

**Files:**
- Modify: `frontend/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: 文言キーを追加**

`</resources>`（288 行付近）の直前に追加:

```xml
    <!-- welcome / sign-in splash -->
    <string name="welcome_tagline">暮らしの在庫を、ちょうどよく。</string>
    <string name="welcome_description">買い忘れも、買い過ぎも減らす。日用品のストックを家族とゆるやかに共有する在庫ノート。</string>
    <string name="welcome_chip_forget">買い忘れ防止</string>
    <string name="welcome_chip_predict">消費予測</string>
    <string name="welcome_chip_share">家族で共有</string>
    <string name="welcome_cta">ログインして始める</string>
    <string name="welcome_cta_busy">Zitadel に接続中…</string>
    <string name="welcome_footer">Zitadel アカウントで安全にサインイン</string>
    <!-- desktop sidebar -->
    <string name="sidebar_switch_subtitle">切り替え・追加</string>
    <string name="sidebar_add_product">商品を追加</string>
    <string name="sidebar_notifications">お知らせ</string>
    <string name="app_name">mindstock</string>
```

- [ ] **Step 2: 生成リソースがコンパイルされることを確認**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: PASS（`Res.string.welcome_tagline` 等が後続タスクで参照可能になる。この時点では未参照だが生成は走る）。

- [ ] **Step 3: コミット**

```bash
git add frontend/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(frontend): ウェルカム splash とサイドバーの文言リソースを追加"
```

---

## Task 4: `WelcomeScreen`（サインイン splash）

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/welcome/WelcomeScreen.kt`

UI のためユニットテストは無し（規約準拠）。コンパイル + 後続の手動確認でゲートする。

- [ ] **Step 1: `WelcomeScreen` を実装**

`frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/welcome/WelcomeScreen.kt`:

```kotlin
package net.brightroom.mindstock.frontend.app.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.app_name
import mindstock.frontend.generated.resources.welcome_chip_forget
import mindstock.frontend.generated.resources.welcome_chip_predict
import mindstock.frontend.generated.resources.welcome_chip_share
import mindstock.frontend.generated.resources.welcome_cta
import mindstock.frontend.generated.resources.welcome_cta_busy
import mindstock.frontend.generated.resources.welcome_description
import mindstock.frontend.generated.resources.welcome_footer
import mindstock.frontend.generated.resources.welcome_tagline
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.ButtonSize
import net.brightroom.mindstock.frontend.designsystem.atom.PrimaryButton
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import org.jetbrains.compose.resources.stringResource

/**
 * 未認証時に表示するウェルカム/サインイン splash。モック `app/screens-a.jsx` の `Login` 準拠。
 * ボタン押下で Zitadel authorize へ redirect する(busy 表示)。
 */
@Composable
fun WelcomeScreen(
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMindstockTokens.current
    var busy by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize().background(tokens.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            // ロゴ + タグライン
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(60.dp)
                            .rotate(-6f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(tokens.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(AppIconName.Box, contentDescription = null, tint = tokens.onAccent, size = 32.dp)
                }
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    AppText(
                        stringResource(Res.string.app_name),
                        style = MindstockType.summaryTitle().copy(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold),
                        color = tokens.ink,
                    )
                    AppText(stringResource(Res.string.welcome_tagline), style = MindstockType.unitCaption(), color = tokens.faint)
                }
            }

            AppText(stringResource(Res.string.welcome_description), style = MindstockType.summarySub(), color = tokens.sub)

            // チップ 3 つ
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Res.string.welcome_chip_forget,
                    Res.string.welcome_chip_predict,
                    Res.string.welcome_chip_share,
                ).forEach { res ->
                    Box(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .border(1.dp, tokens.line, RoundedCornerShape(99.dp))
                                .background(tokens.surface)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        AppText(stringResource(res), style = MindstockType.statusLabel(), color = tokens.sub)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // CTA
            PrimaryButton(
                onClick = {
                    busy = true
                    onSignIn()
                },
                enabled = !busy,
                size = ButtonSize.Lg,
                modifier = Modifier.fillMaxWidth(),
            ) {
                AppText(
                    stringResource(if (busy) Res.string.welcome_cta_busy else Res.string.welcome_cta),
                    color = tokens.onAccent,
                    style = MindstockType.button(),
                )
            }
            AppText(
                stringResource(Res.string.welcome_footer),
                style = MindstockType.unitCaption(),
                color = tokens.faint,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
```

> 注: `PrimaryButton` は `size` 引数を持たない（`AppButton` のラッパ）。下の Step 2 のコンパイルで `size` 引数エラーが出たら `PrimaryButton(...)` を `AppButton(onClick = ..., size = ButtonSize.Lg, enabled = !busy, modifier = ...)` に置換する（`AppButton` は `size`/`variant` を持つ。import を `AppButton` に変更）。`ButtonVariant` 既定は Primary。

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: PASS。`size`/`enabled` 引数エラーが出たら上記注に従い `AppButton` へ置換して再実行。

- [ ] **Step 3: コミット**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/welcome/WelcomeScreen.kt
git commit -m "feat(frontend): モック準拠のウェルカム/サインイン splash 画面を追加"
```

---

## Task 5: `App.kt` に `Unauthenticated → WelcomeScreen` を配線

**Files:**
- Modify: `frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/App.kt`

- [ ] **Step 1: import を追加**

`App.kt` の import 群に追加（`net.brightroom.mindstock.frontend.app.shell.AppShell` の近く）:

```kotlin
import net.brightroom.mindstock.frontend.app.welcome.WelcomeScreen
```

- [ ] **Step 2: `when (state)` に分岐を追加**

`is AuthState.Booting -> { ... }`（162-164 行）の直後に追加:

```kotlin
                is AuthState.Unauthenticated -> {
                    WelcomeScreen(
                        onSignIn = { scope.launch { deps.redirectToAuthorize() } },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
```

> `deps` は `App()` 本体（`val deps = remember { WebAuthDeps(...) }`）で定義され、再認証導線 `deps.redirectToAuthorize()` と同じ呼び出し。`scope` は同ブロック上部の `rememberCoroutineScope()`。

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: PASS。`when` が網羅的なら `Unauthenticated` 追加で未処理警告は出ない。

- [ ] **Step 4: コミット**

```bash
git add frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/App.kt
git commit -m "feat(frontend): 未認証時にウェルカム splash を表示し押下で Zitadel へ"
```

---

## Task 6: `WideShell`（デスクトップ・サイドバー）

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/shell/WideShell.kt`

UI のためユニットテストは無し。コンパイル + 手動確認でゲート。

- [ ] **Step 1: `WideShell` を実装**

`frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/shell/WideShell.kt`:

```kotlin
package net.brightroom.mindstock.frontend.app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.app_name
import mindstock.frontend.generated.resources.sidebar_add_product
import mindstock.frontend.generated.resources.sidebar_notifications
import mindstock.frontend.generated.resources.sidebar_switch_subtitle
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.PrimaryButton
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import org.jetbrains.compose.resources.stringResource

/**
 * 幅 >= 840dp 用のデスクトップ shell。モック `app/app.jsx` の `DesktopChrome` 準拠。
 * 248dp 左サイドバー(ロゴ/世帯スイッチャ/追加/ナビ/お知らせ/ユーザフッタ) + content 中央寄せ。
 * ブラウザ枠(信号機ドット等)はモックのプレゼン足場であり再現しない(実ブラウザがそれ)。
 */
@Composable
fun WideShell(
    selectedTab: Tab,
    onSelectTab: (Tab) -> Unit,
    onAdd: () -> Unit,
    onOpenSwitcher: () -> Unit,
    onBell: () -> Unit,
    displayName: String,
    householdName: String,
    content: @Composable () -> Unit,
) {
    val tokens = LocalMindstockTokens.current
    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // ── サイドバー ──
        Column(
            modifier =
                Modifier
                    .width(248.dp)
                    .fillMaxHeight()
                    .background(tokens.surface)
                    .border(width = 1.dp, color = tokens.lineSoft, shape = RoundedCornerShape(0.dp))
                    .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // ロゴ
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Box(
                    modifier = Modifier.size(36.dp).rotate(-6f).clip(RoundedCornerShape(11.dp)).background(tokens.accent),
                    contentAlignment = Alignment.Center,
                ) { AppIcon(AppIconName.Box, contentDescription = null, tint = tokens.onAccent, size = 20.dp) }
                AppText(
                    stringResource(Res.string.app_name),
                    style = MindstockType.summaryTitle().copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold),
                    color = tokens.ink,
                )
            }

            // 世帯スイッチャ
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, tokens.line, RoundedCornerShape(12.dp))
                        .background(tokens.surface2)
                        .clickable(onClick = onOpenSwitcher)
                        .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(tokens.accentSoft),
                    contentAlignment = Alignment.Center,
                ) { AppIcon(AppIconName.Home, contentDescription = null, tint = tokens.accent, size = 17.dp) }
                Column(modifier = Modifier.weight(1f)) {
                    AppText(householdName, style = MindstockType.cardTitle(), color = tokens.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    AppText(stringResource(Res.string.sidebar_switch_subtitle), style = MindstockType.unitCaption(), color = tokens.faint)
                }
                AppIcon(AppIconName.ChevronRight, contentDescription = null, tint = tokens.faint, size = 15.dp)
            }

            Spacer(Modifier.height(16.dp))

            // 商品を追加
            PrimaryButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                AppIcon(AppIconName.Plus, contentDescription = null, tint = tokens.onAccent, size = 18.dp)
                Spacer(Modifier.width(8.dp))
                AppText(stringResource(Res.string.sidebar_add_product), color = tokens.onAccent, style = MindstockType.button())
            }

            Spacer(Modifier.height(18.dp))

            // ナビ
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Tab.entries.forEach { tab ->
                    val active = tab == selectedTab
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) tokens.accentSoft else Color.Transparent)
                                .clickable { onSelectTab(tab) }
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppIcon(tab.icon, contentDescription = null, tint = if (active) tokens.accent else tokens.sub, size = 20.dp)
                        AppText(stringResource(tab.label), style = MindstockType.button(), color = if (active) tokens.accent else tokens.sub)
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // お知らせ(bell) — 通知機能は将来。Spec1 は present-but-no-op。
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onBell)
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppIcon(AppIconName.Bell, contentDescription = null, tint = tokens.sub, size = 20.dp)
                AppText(stringResource(Res.string.sidebar_notifications), style = MindstockType.button(), color = tokens.sub)
            }

            // 区切り線 + ユーザフッタ
            Box(Modifier.fillMaxWidth().height(1.dp).background(tokens.lineSoft))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Box(
                    modifier = Modifier.size(34.dp).clip(CircleShape).background(tokens.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(
                        displayName.take(1).ifEmpty { "あ" },
                        color = tokens.onAccent,
                        style = MindstockType.statusLabel(),
                    )
                }
                Column {
                    AppText(displayName, style = MindstockType.cardTitle(), color = tokens.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    AppText(householdName, style = MindstockType.unitCaption(), color = tokens.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // ── content(中央寄せ・最大 880dp) ──
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Box(modifier = Modifier.widthIn(max = 880.dp).fillMaxHeight().align(Alignment.TopCenter)) {
                content()
            }
        }
    }
}
```

> 注: サイドバー右境界線は `border` で左/上/下も縁取られる。違和感があれば右辺のみ `Box(Modifier.width(1.dp).fillMaxHeight().background(tokens.lineSoft))` を `Row` 内サイドバーの右隣に置く形へ後で調整可（Spec2 fidelity 範囲）。Spec1 は `border` でよい。

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: PASS。`ChevronRight` は `AppIconName` に存在（確認済み）。`PrimaryButton` の content は `RowScope` なので `Spacer`/`AppIcon`/`AppText` を直接置ける。

- [ ] **Step 3: コミット**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/shell/WideShell.kt
git commit -m "feat(frontend): デスクトップ用 248dp サイドバー shell(WideShell)を追加"
```

---

## Task 7: `AppShell` を分岐 dispatcher 化

**Files:**
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/shell/AppShell.kt`

- [ ] **Step 1: `AppShell` を書き換え**

`app/shell/AppShell.kt` の `AppShell` 関数（`enum class Tab` は変更しない）を次に置換:

```kotlin
@Composable
fun AppShell(
    selectedTab: Tab,
    onSelectTab: (Tab) -> Unit,
    onAdd: () -> Unit,
    onOpenSwitcher: () -> Unit,
    onBell: () -> Unit,
    displayName: String,
    householdName: String,
    stockContent: @Composable () -> Unit,
    shopContent: @Composable () -> Unit,
    activityContent: @Composable () -> Unit,
    profileContent: @Composable () -> Unit,
) {
    val content: @Composable () -> Unit = {
        when (selectedTab) {
            Tab.Stock -> stockContent()
            Tab.Shop -> shopContent()
            Tab.Activity -> activityContent()
            Tab.Profile -> profileContent()
        }
    }
    val widthDp = currentWindowAdaptiveInfo().windowSizeClass.minWidthDp
    when (shellKindFor(widthDp)) {
        ShellKind.Wide ->
            WideShell(
                selectedTab = selectedTab,
                onSelectTab = onSelectTab,
                onAdd = onAdd,
                onOpenSwitcher = onOpenSwitcher,
                onBell = onBell,
                displayName = displayName,
                householdName = householdName,
                content = content,
            )

        ShellKind.Compact ->
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                Box(modifier = Modifier.fillMaxSize().padding(bottom = 88.dp)) { content() }
                BottomNav(
                    selected = selectedTab,
                    onSelect = onSelectTab,
                    onAdd = onAdd,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
    }
}
```

- [ ] **Step 2: import を調整**

`AppShell.kt` の import に追加:

```kotlin
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
```

> `currentWindowAdaptiveInfo()` は `compose-adaptive`（依存済み）の API。返り値の `windowSizeClass.minWidthDp`（Int, dp 値）を幅として使う。もし `minWidthDp` プロパティ名がバージョン差で異なる場合は `windowSizeClass.windowWidthSizeClass` 経由ではなく `currentWindowSize()` から幅を取り `density` で割る方法に切替（下記 Step 3 の注参照）。既存の他 import（`Box`/`fillMaxSize`/`padding`/`MaterialTheme`/`Alignment` 等）はそのまま使う。

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: PASS。

> 注（幅の取得が API 差でコンパイルしない場合のフォールバック）: `currentWindowAdaptiveInfo().windowSizeClass.minWidthDp` が解決しないときは、次に置換する:
> ```kotlin
> import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
> import androidx.compose.ui.platform.LocalDensity
> // ...
> val sizeClass = currentWindowAdaptiveInfo().windowSizeClass
> val isWide = sizeClass.isWidthAtLeastBreakpoint(840) // 1.3.0-beta01 の API
> when (if (isWide) ShellKind.Wide else ShellKind.Compact) { ... }
> ```
> さらに解決しなければ `BoxWithConstraints { val widthDp = maxWidth.value.toInt(); when (shellKindFor(widthDp)) { ... } }` で確実に動く（`shellKindFor` 純関数はどの経路でも再利用）。`BoxWithConstraints` 経路を採る場合は `content`/各 shell をその中に入れる。

- [ ] **Step 4: コミット**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/shell/AppShell.kt
git commit -m "feat(frontend): AppShell を幅で Wide/Compact に分岐する dispatcher 化"
```

---

## Task 8: `App.kt` の `AppShell` 呼び出しに新引数を渡す

**Files:**
- Modify: `frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/App.kt:313-369`

- [ ] **Step 1: `AppShell(` 呼び出しに新引数を追加**

`App.kt` 312 行 `var selectedTab by remember { mutableStateOf(Tab.Stock) }` の直後、`AppShell(` 呼び出し（313 行〜）の引数 `onAdd = { catalogOverlay = CatalogOverlay.AddProduct },` の直後に、以下のローカル算出と引数を追加する。まず 312 行と 313 行の間に算出を挿入:

```kotlin
                        val shellHousehold =
                            sessionState.households?.list?.firstOrNull { it.id == householdId }
```

次に `AppShell(` の引数リスト（`onAdd = ...,` の行の直後）に追加:

```kotlin
                            onOpenSwitcher = { settingsSheet = SettingsSheet.Switcher },
                            onBell = {},
                            displayName = sessionState.displayName?.invoke() ?: "",
                            householdName = shellHousehold?.profile?.name?.invoke() ?: "",
```

> `stockContent` ラムダ内に既にある `val activeHousehold = sessionState.households?.list?.firstOrNull { it.id == householdId }`（318-319 行）はそのまま残してよい（重複算出だが挙動に影響なし）。気になる場合は `stockContent` 内の `activeHousehold` を新設 `shellHousehold` に置換し、その行を削除する。`onBell = {}` は Spec1 の no-op（bell=通知は将来）。

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: PASS（`AppShell` の新シグネチャに全引数が揃う）。

- [ ] **Step 3: コミット**

```bash
git add frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/App.kt
git commit -m "feat(frontend): AppShell にサイドバー用の世帯名/表示名/スイッチャ導線を配線"
```

---

## Task 9: 仕上げ検証（コンパイル + テスト + 手動 eyeball）

**Files:** なし（検証のみ）

- [ ] **Step 1: 全体コンパイル**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: PASS。

- [ ] **Step 2: テスト**

Run: `./gradlew :frontend:wasmJsTest`（重ければ `./gradlew :frontend:compileTestKotlinWasmJs`）
Expected: `ShellKindTest` 4 件 + `AuthViewModelTest`（`no_token_becomes_unauthenticated` 含む）緑。

- [ ] **Step 3: 手動 eyeball（dev server）**

Run: `./gradlew :frontend:wasmJsBrowserDevelopmentRun`
確認:
- ブラウザ幅 ≥ 840px で 248dp 左サイドバー shell。ナビ4項目でタブ遷移、active が `accentSoft`。商品を追加 / 世帯スイッチャが機能。
- 幅を 840px 未満に縮めると浮遊ボトムナビへ動的に切替。
- 未認証(token 無し/ログアウト後)で起動するとウェルカム splash。「ログインして始める」で Zitadel へ redirect。

> dev server が OOM / 2nd Zitadel ID 等で通せない場合はユーザに共有し、スクショ確認を依頼する（Spec2 の忠実度スイープと同じ運用）。手動確認が出来なくても Step 1/2 が緑なら構造実装は完了とみなし、見た目調整は Spec2 へ。

- [ ] **Step 4: 完了報告**

実装完了。受け入れ条件（spec 末尾）との対応を確認し、Spec2（P6-4b 忠実度スイープ）へ引き継ぐ。

---

## Self-Review（計画作成者チェック結果）

- **Spec coverage**: ブレークポイント=Task1 / AuthState.Unauthenticated+boot=Task2 / WelcomeScreen=Task3,4,5 / WideShell=Task6 / AppShell 分岐+reflow 土台=Task7,8 / テスト=Task1,2 / 受け入れ条件=Task9。全 spec 節に対応タスクあり。
- **Placeholder scan**: 「TBD/TODO/後で」等なし。各 step に実コード/実コマンド/期待出力あり。
- **Type consistency**: `AppShell` 新シグネチャ（`onOpenSwitcher`/`onBell`/`displayName`/`householdName` 追加）は Task7 定義と Task8 呼び出しで一致。`WideShell` の引数（Task6）と `AppShell` からの呼び出し（Task7）一致。`shellKindFor`/`ShellKind`（Task1）を Task7 で参照。`AuthState.Unauthenticated`（Task2）を Task5 で参照。文言キー（Task3）を Task4/Task6 で参照。
- **既知リスク**: `currentWindowAdaptiveInfo().windowSizeClass` の幅取得 API はバージョン差がありうる（Task7 Step3 に `isWidthAtLeastBreakpoint` / `BoxWithConstraints` の2段フォールバックを明記）。`PrimaryButton` の `size` 非対応（Task4 Step1 に `AppButton` への置換注記）。
