# フェーズ 4: frontend 構造リファクタ Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** App.kt への配線集中と ViewModel 間のコピペ(`handleFailure` / load-mutation エラー二重表示 / アバター・セクション見出しの散在 / フォント再構築)を解消し、見た目を一切変えずに frontend の構造負債を返済する。

**Architecture:** 純粋な構造リファクタ。RPC 契約・ドメイン・画面の見た目はゼロ変更。安全網は既存の充実した commonTest VM スイート(InventoryViewModelTest 等)+ `compileKotlinWasmJs` + dev server 実描画 eyeball。ViewModel のコンストラクタ引数は変えない(既存テストの `vm()` ヘルパーを無改修に保つため)ことを最優先制約とする。

**Tech Stack:** Kotlin Multiplatform / Compose Multiplatform (Kotlin/Wasm) / kotlinx-coroutines Flow / compose-resources(strings)。テストは kotlin.test `@Test` + Kotest assertions + `runTest`/`runCurrent`。

---

## 確定した設計判断(実装中に再燃させない)

- **4-4 の共通化形態 = collaborator クラス `FailureHandler(reauth, toast)`**(拡張関数でなく)。理由: SettingsViewModel の `failWith`(Conflict 専用文言)をオーバーロードで自然に吸収でき、各 VM はコンストラクタを変えずに `private val failure = FailureHandler(reauth, toast)` を1行持つだけで済む。置き場は `core/ui/`(UI フィードバックの層。`errorText` は `core/rpc`、`ReauthController` は `core/auth`、`ToastController` は `core/ui` に跨るが、利用面は UI フィードバック)。
- **4-5 のエラー UX 分類**: load 失敗 = 期限切れなら `reauth.request()`・それ以外は `UiState.Error(errorText)` の画面表示のみ(**トーストしない**=二重表示の解消)。mutation(write)失敗 = 従来どおり `FailureHandler` でトーストのみ(画面状態は変えない)。`FailureHandler` に `onMutationFailure` / `onLoadFailure` の2系統を持たせて両者を表現する。
- **4-2**: `ProductMasterViewModel` は Master/Settings 両ブランチで共有する単一 `remember`。Settings ブランチが load 系を使わない件は write 専用 interface 分離まではせず共有 VM で許容(マスタープラン明記)。
- **4-7 の OccurredAt 供給点**: 買い物リストのクイック補充に日付ピッカーは無いので時刻は「now」で正しい。ただし `OccurredAt.now()` を App.kt の `remember` クロージャに埋めるのをやめ、VM が `OccurredAt` 引数を受ける形(Inventory/ProductDetail と対称)にして、UI コールバック境界で `OccurredAt.now()` を供給する。
- コミットメッセージに issue/PR 番号を書かない(working agreement)。

---

## File Structure

新規作成:
- `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/ui/FailureHandler.kt` — RPC 失敗 → reauth/トースト/画面エラーの分類を一手に持つ collaborator(4-4 / 4-5)。
- `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/AvatarBadge.kt` — 頭文字+利用者別色の円形アバター atom(4-9)。
- `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/SectionLabel.kt` — セクション見出し atom(4-9)。
- `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/core/ui/FailureHandlerTest.kt` — FailureHandler の単体テスト。

修正(主要):
- `frontend/src/webMain/.../App.kt`(695 行)— Ready ブランチの関数分割(4-1)、ProductMasterViewModel 二重生成解消(4-2)、`LoadWithRefresh` 集約(4-3)、`ProductSettingsSheetWithImage` の状態を VM へ(4-8)、ShoppingList 補充の OccurredAt 配線(4-7)。
- 9 ViewModel — `handleFailure`/`failWith` を `FailureHandler` 利用へ(4-4)、load/mutation エラー UX 分類(4-5)。
- `InventoryViewModel.kt` — `_view`/`_query` の手動二重管理を `combine`+`stateIn` へ(4-6)。
- `ShoppingListViewModel.kt` — `replenishStock` に `OccurredAt`(4-7)。
- `OnboardingViewModel.kt` / `NeedHouseholdViewModel.kt` — `runCatching{}.getOrNull()` → `try/catch`(4-12)。
- `core/auth/AuthState.kt` + `AuthViewModel.kt` — `Failed.message: String` → `UiText`(4-11)。
- `designsystem/theme/MindstockType.kt` — `notoSansJpFamily()` の毎回再構築解消(4-10)。
- `designsystem/atom/HouseholdPill.kt` — `"$memberCount 人"` を `stringResource` へ(4-13)。
- アバター散在 3 箇所(SettingsScreen.kt / ProductDetailScreen.kt / MemberSheet.kt)・SectionLabel 散在 3 箇所(SettingsScreen.kt / ShoppingListScreen.kt / AddProductScreen.kt)を新 atom 呼び出しへ置換(4-9)。
- `feature/catalog/ui/ArchivedScreen.kt:134` — エラー色 `tokens.sub` → `tokens.statusOut`(4-5)。
- strings.xml — `auth_failed_login` / `auth_failed_boot` を追加(4-11)。
- `.claude/rules/frontend-rpc-and-error.md` — `handleFailure` 共通化(FailureHandler)を反映(R-8 追従 / 実行プロトコル 6)。

---

## 実装順序

leaf/atom/VM の独立変更(Task 1〜8)を先に、最も波及の大きい App.kt 分割(Task 9)を最後に行う。Task 7(ShoppingList OccurredAt)は App.kt の shop 配線を変えるため、App.kt 分割(Task 9)はその後に行うと手戻りが少ない。各タスク末尾でコミット。

---

## Task 1: フォント family の毎回再構築を解消(4-10)

**Files:**
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/theme/MindstockType.kt:33`

**背景:** `MindstockType.style()`(:33)が `fontFamily = notoSansJpFamily()` を毎呼び出しで実行し、`FontFamily(...)`(9 フォント)を毎回新規構築している。`MindstockType.xxx()` は描画のたびに呼ばれるため無駄。`@Composable` 文脈なので `remember` で 1 度だけ構築する。

- [ ] **Step 1: `style()` 内で family を remember**

`MindstockType.kt` の `style()` 関数を次に変更(`import androidx.compose.runtime.remember` を追加):

```kotlin
    @Composable
    private fun style(
        weight: FontWeight,
        size: Float,
        lineHeight: Float,
        letterSpacingEm: Float = 0f,
        features: String? = null,
    ): TextStyle {
        val family = notoSansJpFamily()
        return TextStyle(
            fontFamily = family,
            fontWeight = weight,
            fontSize = size.sp,
            lineHeight = (size * lineHeight).sp,
            lineHeightStyle = trimmed,
            letterSpacing = letterSpacingEm.em,
            fontFeatureSettings = features,
        )
    }
```

実体の memoize は `notoSansJpFamily()` 側に持たせる(全呼び出し元が裨益するため)。Typography.kt の `notoSansJpFamily()` を次に変更:

```kotlin
@Composable
fun notoSansJpFamily(): FontFamily =
    remember {
        FontFamily(
            Font(Res.font.NotoSansJP_Thin, FontWeight.Thin, FontStyle.Normal),
            Font(Res.font.NotoSansJP_ExtraLight, FontWeight.ExtraLight, FontStyle.Normal),
            Font(Res.font.NotoSansJP_Light, FontWeight.Light, FontStyle.Normal),
            Font(Res.font.NotoSansJP_Regular, FontWeight.Normal, FontStyle.Normal),
            Font(Res.font.NotoSansJP_Medium, FontWeight.Medium, FontStyle.Normal),
            Font(Res.font.NotoSansJP_SemiBold, FontWeight.SemiBold, FontStyle.Normal),
            Font(Res.font.NotoSansJP_Bold, FontWeight.Bold, FontStyle.Normal),
            Font(Res.font.NotoSansJP_ExtraBold, FontWeight.ExtraBold, FontStyle.Normal),
            Font(Res.font.NotoSansJP_Black, FontWeight.Black, FontStyle.Normal),
        )
    }
```

`import androidx.compose.runtime.remember` を Typography.kt に追加。`Res.font.*` の `Font(...)` は Composable 関数のため、`remember { }` ブロック内で呼べることを確認(compose-resources の `Font` は `@Composable`)。**もし `Font` が `@Composable` で `remember{}` 内から呼べずコンパイルエラーになる場合**は、Step 1 の MindstockType.style() 内の `val family = notoSansJpFamily()` を `val family = remember { ... }` にはできない(同じ理由)。その場合のフォールバックは「CompositionLocal `LocalNotoSansJpFamily` を MindstockTheme で 1 度 provide し、`style()` は `LocalNotoSansJpFamily.current` を読む」方式に切替える(下記 Step 1-alt)。

- [ ] **Step 1-alt(Step 1 がコンパイル不可だった場合のみ): CompositionLocal 化**

`Typography.kt` に追加:

```kotlin
val LocalNotoSansJpFamily = staticCompositionLocalOf<FontFamily> { error("LocalNotoSansJpFamily not provided") }
```

`MindstockTheme.kt` の最上位で `notoSansJpFamily()` を 1 度評価して provide:

```kotlin
@Composable
fun MindstockTheme(content: @Composable () -> Unit) {
    val fontFamily = notoSansJpFamily()
    CompositionLocalProvider(
        LocalMindstockTokens provides clayTokens,
        LocalNotoSansJpFamily provides fontFamily,
        // 既存の provide はそのまま
    ) {
        // 既存の MaterialTheme ラップはそのまま
    }
}
```

`MindstockType.style()` は `fontFamily = LocalNotoSansJpFamily.current` を読む(`@Composable` のまま)。

- [ ] **Step 2: コンパイル**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: コミット**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/theme/
git commit -m "refactor(frontend): NotoSansJP FontFamily の毎回再構築を解消"
```

---

## Task 2: HouseholdPill の人数ハードコードを i18n 化(4-13)

**Files:**
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/HouseholdPill.kt:70`

**背景:** `"$memberCount 人"`(:70)がハードコード。strings.xml:73 に `household_member_count = "%1$d 人"` が定義済み。i18n ルール違反の解消。

- [ ] **Step 1: stringResource へ置換**

HouseholdPill.kt の該当 `AppText`:

```kotlin
        AppText(
            stringResource(Res.string.household_member_count, memberCount),
            style = MindstockType.statusLabel().copy(fontSize = 11f.sp, lineHeight = 11f.sp),
            color = tokens.faint,
        )
```

import を追加:

```kotlin
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.household_member_count
import org.jetbrains.compose.resources.stringResource
```

- [ ] **Step 2: コンパイル**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: コミット**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/HouseholdPill.kt
git commit -m "refactor(frontend): HouseholdPill の人数表示を strings リソース化"
```

---

## Task 3: AvatarBadge / SectionLabel を atom へ昇格(4-9)

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/AvatarBadge.kt`
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/SectionLabel.kt`
- Modify: `app/settings/SettingsScreen.kt`(アバター :532-537 / SectionLabel :620-628)
- Modify: `feature/inventory/ui/ProductDetailScreen.kt`(アバター :447-450)
- Modify: `feature/household/ui/MemberSheet.kt`(アバター :69-73)
- Modify: `feature/shopping/ui/ShoppingListScreen.kt`(SectionLabel :250-258)
- Modify: `feature/catalog/ui/AddProductScreen.kt`(SectionLabel :657-665)

**背景:** 頭文字+`avatarColorOf`+白文字の円形バッジが 3 箇所(サイズ 18/38/52・フォント 9/15/21・onAccent or Color.White の差)。`SectionLabel` も 3 画面に private 定義(スタイル差あり)。共通 atom にしてサイズ・スタイル差をパラメータ化する。

- [ ] **Step 1: AvatarBadge atom を作成**

`AvatarBadge.kt`:

```kotlin
package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import net.brightroom.mindstock.frontend.designsystem.theme.avatarColorOf

/**
 * 利用者別色 + 白頭文字の円形アバター。mock のメンバー/履歴アバター(`avatarColorOf` 塗り + 頭文字)を統合。
 * フォントサイズはバッジ径に比例(径の約 0.4 倍)。色は表示名から決定的に決まる([avatarColorOf])。
 */
@Composable
fun AvatarBadge(
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(99.dp = size)).background(avatarColorOf(name)),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            name.take(1),
            style = MindstockType.cardTitle().copy(fontSize = (size.value * 0.4f).sp, lineHeight = (size.value * 0.4f).sp),
            color = Color.White,
        )
    }
}
```

**注意(実装時に必ず確認):** 上記の `RoundedCornerShape(99.dp = size)` は誤記。`clip` は完全円なので `androidx.compose.foundation.shape.CircleShape` を使う(`import androidx.compose.foundation.shape.CircleShape`、`.clip(CircleShape)`)。3 箇所の現物は `RoundedCornerShape(99.dp)`(38/52)と `CircleShape`(18)が混在しているが、どちらも径 ≤ 52dp では完全円で視覚同一。atom では `CircleShape` に統一する。フォント倍率は現物の比率を踏襲: 18→9(0.5)、38→15(0.39)、52→21(0.40)。**完全一致のため、倍率でなくサイズ→フォントの対応を明示パラメータ `textSize: Dp? = null`(null なら `size * 0.4`)にし、呼び出し側で現物の値(9/15/21sp)を明示的に渡す**。最終形:

```kotlin
@Composable
fun AvatarBadge(
    name: String,
    size: Dp,
    textSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(avatarColorOf(name)),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            name.take(1),
            style = MindstockType.cardTitle().copy(fontSize = textSize, lineHeight = textSize),
            color = Color.White,
        )
    }
}
```

`import androidx.compose.ui.unit.TextUnit` / `import androidx.compose.foundation.shape.CircleShape` を使用。**色の注意**: 現物は SettingsScreen が `tokens.onAccent`、ProductDetail が `tokens.onAccent`、MemberSheet が `Color.White` と微差がある。`onAccent = Color(0xFFFFFBF4)`(ほぼ白)。atom では `Color.White` に統一して良いか確認 — 視覚差は無視できる(0xFFFBF4 vs 0xFFFFFF)が、忠実度厳守のため **`Color.White` に統一し、差異は「微差のため統一」とコミットメッセージに明記**。

- [ ] **Step 2: SectionLabel atom を作成**

3 箇所のスタイル差: Settings=`summarySub()`(500/12.5)・Shopping=`statusLabel().copy(Bold,12sp)`(700/12)・AddProduct=`sectionMeta()`(600/13)。スタイルとパディングをパラメータ化:

```kotlin
package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType

/**
 * 小見出し(セクションラベル)。mock のセクション見出しを統合。
 * スタイルは画面ごとに差があるため [style] でパラメータ化(既定は sectionMeta=600/13)。色は faint。
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MindstockType.sectionMeta(),
) {
    val tokens = LocalMindstockTokens.current
    AppText(text, style = style, color = tokens.faint, modifier = modifier)
}
```

呼び出し側はそれぞれ現物の style と padding を `modifier`/`style` 引数で渡す:
- SettingsScreen: `SectionLabel(text, modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 10.dp), style = MindstockType.summarySub())`
- ShoppingListScreen: `SectionLabel(text, modifier = Modifier.padding(start = 4.dp), style = MindstockType.statusLabel().copy(fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 12.sp))`
- AddProductScreen: `SectionLabel(text, modifier = Modifier.padding(start = 2.dp))`(既定 style = sectionMeta)

- [ ] **Step 3: 散在実装を atom 呼び出しへ置換**

各ファイルの private `SectionLabel`/アバター Box を削除し、上記 atom 呼び出しに置換。アバター置換例:
- SettingsScreen.kt:532-537 → `AvatarBadge(member.name, size = 38.dp, textSize = 15.sp)`
- ProductDetailScreen.kt:447-450 → `AvatarBadge(name, size = 18.dp, textSize = 9.sp)`
- MemberSheet.kt:69-73 → `AvatarBadge(m.name, size = 52.dp, textSize = 21.sp)`

各ファイルで不要になった import(`avatarColorOf`, `CircleShape`/`RoundedCornerShape`, `Box`, `background`, `clip` 等)が他で使われていないか確認して整理。

- [ ] **Step 4: コンパイル + テスト**

Run: `./gradlew :frontend:compileKotlinWasmJs && ./gradlew :frontend:allTests`
Expected: BUILD SUCCESSFUL(既存スナップショット系テストは無いので回帰なし)

- [ ] **Step 5: コミット**

```bash
git add frontend/src/commonMain/
git commit -m "refactor(frontend): アバター/セクション見出しを atom へ昇格し散在実装を統合"
```

---

## Task 4: FailureHandler collaborator + エラー UX 分類(4-4 / 4-5)

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/ui/FailureHandler.kt`
- Create: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/core/ui/FailureHandlerTest.kt`
- Modify: 9 ViewModel(Activity / AddProduct / Archived / ProductMaster / NeedHousehold / Settings / Inventory / ProductDetail / ShoppingList)
- Modify: `feature/catalog/ui/ArchivedScreen.kt:134`
- Modify: `.claude/rules/frontend-rpc-and-error.md`

**背景:** 8 VM が同一の `private fun handleFailure(error) { if (requiresReauth) reauth.request() else toast.show(errorText(error)) }` をコピペ。SettingsViewModel のみ `failWith`(Conflict 専用文言)。さらに load() 失敗が「toast + Error 画面」の二重表示になっている。

- [ ] **Step 1: FailureHandlerTest を書く(失敗する)**

`FailureHandlerTest.kt`:

```kotlin
package net.brightroom.mindstock.frontend.core.ui

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.error_conflict
import mindstock.frontend.generated.resources.settings_error_last_owner_leave
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.rpc.result.RpcError
import kotlin.test.Test

class FailureHandlerTest {
    @Test
    fun mutation_unauthorized_requests_reauth_no_toast() =
        runTest {
            var reauthed = 0
            val reauth = ReauthController()
            val job = launch { reauth.signal.collect { reauthed++ } }
            runCurrent()
            val toast = ToastController()
            FailureHandler(reauth, toast).onMutationFailure(RpcError.Unauthorized("x"))
            runCurrent()
            reauthed shouldBe 1
            toast.current.value shouldBe null
            job.cancel()
        }

    @Test
    fun mutation_other_error_toasts() =
        runTest {
            val toast = ToastController()
            FailureHandler(ReauthController(), toast).onMutationFailure(RpcError.Internal("x"))
            (toast.current.value != null) shouldBe true
        }

    @Test
    fun mutation_conflict_with_text_uses_conflict_text() =
        runTest {
            val toast = ToastController()
            FailureHandler(ReauthController(), toast)
                .onMutationFailure(RpcError.Conflict("dup"), Res.string.settings_error_last_owner_leave)
            toast.current.value
                ?.text
                ?.resource shouldBe Res.string.settings_error_last_owner_leave
        }

    @Test
    fun load_unauthorized_requests_reauth_but_no_toast() =
        runTest {
            var reauthed = 0
            val reauth = ReauthController()
            val job = launch { reauth.signal.collect { reauthed++ } }
            runCurrent()
            val toast = ToastController()
            FailureHandler(reauth, toast).onLoadFailure(RpcError.Unauthorized("x"))
            runCurrent()
            reauthed shouldBe 1
            toast.current.value shouldBe null
            job.cancel()
        }

    @Test
    fun load_other_error_does_not_toast() =
        runTest {
            val toast = ToastController()
            FailureHandler(ReauthController(), toast).onLoadFailure(RpcError.Internal("x"))
            toast.current.value shouldBe null
        }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :frontend:allTests --tests "*FailureHandlerTest*"`
Expected: FAIL(`FailureHandler` 未定義のコンパイルエラー)

- [ ] **Step 3: FailureHandler を実装**

`FailureHandler.kt`:

```kotlin
package net.brightroom.mindstock.frontend.core.ui

import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.errorText
import net.brightroom.mindstock.frontend.core.rpc.requiresReauth
import net.brightroom.mindstock.rpc.result.RpcError
import org.jetbrains.compose.resources.StringResource

/**
 * RPC 失敗の共通ハンドラ。各 ViewModel が `reauth`/`toast` から 1 つ生成して使う。
 * - [onMutationFailure]: 書込失敗。期限切れは再認証、それ以外はトースト(画面状態は変えない)。
 * - [onLoadFailure]: 読込失敗。期限切れのみ再認証。文言は呼び出し側が UiState.Error に出す(二重表示しない)。
 */
class FailureHandler(
    private val reauth: ReauthController,
    private val toast: ToastController,
) {
    fun onMutationFailure(error: RpcError) {
        if (error.requiresReauth()) reauth.request() else toast.show(errorText(error))
    }

    /** Conflict かつ [conflictText] 指定時は専用文言、それ以外は [onMutationFailure] と同じ。 */
    fun onMutationFailure(
        error: RpcError,
        conflictText: StringResource?,
    ) {
        if (error.requiresReauth()) {
            reauth.request()
            return
        }
        if (error is RpcError.Conflict && conflictText != null) {
            toast.show(UiText(conflictText))
            return
        }
        toast.show(errorText(error))
    }

    fun onLoadFailure(error: RpcError) {
        if (error.requiresReauth()) reauth.request()
    }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :frontend:allTests --tests "*FailureHandlerTest*"`
Expected: PASS

- [ ] **Step 5: 各 VM を FailureHandler へ移行**

各 VM で `private fun handleFailure(...)`(または `failWith`)定義を削除し、`private val failure = FailureHandler(reauth, toast)` を追加。**コンストラクタ引数は変えない**(`reauth`/`toast` は既に受けている)。呼び出しを置換:

- **mutation/write 失敗**(`write()` 内・search/lookup/submit・create/join・各 mutation): `handleFailure(x)` → `failure.onMutationFailure(x)`
- **load 失敗**(load() 内): `handleFailure(x)` を **削除**し、`failure.onLoadFailure(x)` に置換(reauth のみ。Error 画面表示は既存の `UiState.Error(errorText(...))` がそのまま担う=トースト消滅で二重表示解消)。

VM 別の具体置換:

1. **ActivityViewModel** (load のみ): load の `if (requiresReauth) reauth.request() else toast.show(errorText(out.error))` → `failure.onLoadFailure(out.error)`。`toast`/`reauth`/`errorText`/`requiresReauth` の不要 import 整理(`errorText` は `UiState.Error(errorText(...))` でなお使うので残す)。
2. **ArchivedViewModel**: load(:41) → `failure.onLoadFailure`、unarchive(:56) → `failure.onMutationFailure`。
3. **ProductMasterViewModel**: load(:49) → `onLoadFailure`、imageWrite(:91)/write(:108) → `onMutationFailure`。
4. **NeedHouseholdViewModel**: create(:50)/join(:91) → `onMutationFailure`(load なし)。
5. **AddProductViewModel**: search(:58)/lookupByJan(:78)/submit(:131) → `onMutationFailure`(load なし)。
6. **InventoryViewModel**: load(:53) → `onLoadFailure`、write(:97) → `onMutationFailure`。
7. **ProductDetailViewModel**: load の 2 箇所(:65,:84) → `onLoadFailure`、write(:130) → `onMutationFailure`。
8. **ShoppingListViewModel**: load(:46) → `onLoadFailure`、write(:78) → `onMutationFailure`。
9. **SettingsViewModel**: `failWith(error, lastOwner)` 定義(:215-229)を削除。全呼び出し(:110,:156,:172,:188,:210)を `failure.onMutationFailure(error, lastOwner)` へ。`failWith(r.error, null)` は `failure.onMutationFailure(r.error)`(または `onMutationFailure(r.error, null)`)。Settings は load を持たない。`StringResource` import は維持。

各 VM で `requiresReauth` import が他で使われなくなったら削除。`errorText` は load の `UiState.Error(errorText(...))` で残る VM がある(Activity/Archived/ProductMaster/Inventory/ProductDetail/ShoppingList)。

- [ ] **Step 6: ArchivedScreen のエラー色是正(4-5)**

`ArchivedScreen.kt:134` の `color = tokens.sub` → `color = tokens.statusOut`(他画面の Error 表示色と統一)。

- [ ] **Step 7: 全テスト + コンパイル**

Run: `./gradlew :frontend:allTests`
Expected: PASS(既存の `load_failure_sets_error` 系はトースト有無を検査しないので緑。`unauthorized_*` 系は reauth 経路を維持するので緑)

- [ ] **Step 8: ルール追従(R-8)**

`.claude/rules/frontend-rpc-and-error.md` の `handleFailure` 記述を、`core/ui/FailureHandler`(`onMutationFailure`/`onLoadFailure`、Conflict オーバーロード、load は二重表示しない方針)に更新。`RpcOutcome`/`toOutcome` の記述は既存のままで可。

- [ ] **Step 9: コミット**

```bash
git add frontend/src/ .claude/rules/frontend-rpc-and-error.md
git commit -m "refactor(frontend): RPC 失敗処理を FailureHandler に集約しエラー二重表示を解消"
```

---

## Task 5: InventoryViewModel の状態を単一フロー合成へ(4-6)

**Files:**
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/InventoryViewModel.kt`
- Test(既存): `frontend/src/commonTest/.../inventory/InventoryViewModelTest.kt`(無改修で緑を維持)

**背景:** `_view`/`_query` を独立 StateFlow で持ちつつ、`_state`(Content の view/query フィールド)にも手動コピー、`load()` も `_view.value`/`_query.value` を読む二重管理。`combine`+`stateIn` で「サーバ取得結果 × view × query」の単一合成にする。

**制約:** 公開 API(`state: StateFlow<InventoryUiState>`, `view`, `query`, `load()`, `setView()`, `setQuery()`, `replenish()`, `consume()`)とコンストラクタ引数は不変。既存テスト(`load_success_sets_content` / `load_failure_sets_error` / `query_survives_reload_after_write` / `search_filters_visible_stocks` / `replenish_*`)を無改修で緑に保つ。

- [ ] **Step 1: 現行 InventoryViewModel.kt を読み、合成設計を確定**

`InventoryUiState`(`InventoryUiState.kt`)の `Content(stocks, view, query)` / `Loading` / `Error(message)` の形と、`load()` が成功時に `Content(out.value, _view.value, _query.value)` を作る点を確認。合成方針:

- `private val _loadResult = MutableStateFlow<RpcOutcome<Stocks>?>(null)`(最後の取得結果。null=未ロード)
- `_view` / `_query` は MutableStateFlow のまま(ユーザ操作 source of truth)
- `state` を `combine(_loadResult, _view, _query) { result, view, query -> ... }.stateIn(viewModelScope, SharingStarted.Eagerly, InventoryUiState.Loading)` で導出
- `load()` は `_loadResult.value = loadStocks(householdId)` を行い、失敗時に `failure.onLoadFailure` を呼ぶだけ(state は combine が更新)
- `setView`/`setQuery` は `_view.value=`/`_query.value=` のみ(`_state` 手動同期コードを削除)

合成ラムダ:

```kotlin
val state: StateFlow<InventoryUiState> =
    combine(_loadResult, _view, _query) { result, view, query ->
        when (result) {
            null -> InventoryUiState.Loading
            is RpcOutcome.Success -> InventoryUiState.Content(result.value, view, query)
            is RpcOutcome.Failure -> InventoryUiState.Error(errorText(result.error))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, InventoryUiState.Loading)
```

`load()`:

```kotlin
suspend fun load() {
    _loadResult.value = null // Loading 表示
    val out = loadStocks(householdId)
    if (out is RpcOutcome.Failure) failure.onLoadFailure(out.error)
    _loadResult.value = out
}
```

`view`/`query` 公開プロパティは `_view.asStateFlow()`/`_query.asStateFlow()` を維持(参照元があるか確認。無ければ削除可だが、互換のため維持)。`setView`/`setQuery`:

```kotlin
fun setView(v: StockView) { _view.value = v }
fun setQuery(query: String) { _query.value = query }
```

import 追加: `androidx.lifecycle.viewModelScope` / `kotlinx.coroutines.flow.SharingStarted` / `kotlinx.coroutines.flow.combine` / `kotlinx.coroutines.flow.stateIn`。`asStateFlow` は view/query で残る。

**注意:** `load()` が `_loadResult.value = null` を挟むと、combine が一瞬 `Loading` を出す → 既存テスト `query_survives_reload_after_write` は最終 `Content.query == "milk"` を見るので問題ない(null→Success で Content に戻り query は `_query` 由来で保持)。`Loading` の瞬間挿入が visual にちらつく懸念があれば、`load()` で `_loadResult.value = null` を**省き**、取得後に結果を入れるだけにする(初回は Eagerly の初期値 Loading が出る)。**既定は null を省く**(再フェッチ中に一覧が消えるちらつきを避ける。現行の write→load も Content のまま再取得していた)。最終形の `load()`:

```kotlin
suspend fun load() {
    val out = loadStocks(householdId)
    if (out is RpcOutcome.Failure) failure.onLoadFailure(out.error)
    _loadResult.value = out
}
```

ただし初回 load 前に明示 Loading を出したい既存挙動(`_state.value = InventoryUiState.Loading`)があるため、初期値 Loading(stateIn の initialValue)で代替されることを確認。`load_success_sets_content` は load 後に Content を期待 → 緑。

- [ ] **Step 2: 実装して既存テストで検証**

InventoryViewModel.kt を上記へ書き換え(Task 4 の `failure` も導入済み前提)。

Run: `./gradlew :frontend:allTests --tests "*InventoryViewModelTest*" --tests "*InventoryUiStateTest*"`
Expected: PASS(6 テスト全緑)

- [ ] **Step 3: 退行確認のため view 切替テストを 1 本追加**

`InventoryViewModelTest.kt` に追加(`combine` 合成が view 変更を反映することの明示):

```kotlin
    @Test
    fun set_view_reflects_in_content() =
        runTest {
            val v = vm()
            v.load()
            v.setView(StockView.Grid)
            val content = v.state.value as InventoryUiState.Content
            content.view shouldBe StockView.Grid
        }
```

`StockView` の実際の variant 名(`List`/`Grid` 等)は `InventoryUiState.kt` を読んで合わせる。`stateIn` の collect は `runTest` 下で `state.value` 参照時に評価されるが、`SharingStarted.Eagerly` でも cold な combine は購読が無いと最新値を出さない場合がある — **`v.state.value` が最新を返さない場合は `SharingStarted.Eagerly` + `runCurrent()` を挟む**か、テストで `backgroundScope.launch { v.state.collect {} }; runCurrent()` を行う。既存テストが `v.state.value` 直読みで通っている現行(MutableStateFlow 直持ち)と異なり、`stateIn` は購読依存。**この差で既存テストが落ちる場合**は Step 1 の方針を「`state` は `MutableStateFlow` のまま保持し、combine 結果を `viewModelScope` で collect して `_state` に流し込む(internal）」へ切替(下記 Step 3-alt)。

- [ ] **Step 3-alt(stateIn で既存テストが落ちた場合のみ)**

`state` を `private val _state = MutableStateFlow<InventoryUiState>(Loading)` + `val state = _state.asStateFlow()` に戻し、init で合成を流し込む:

```kotlin
init {
    viewModelScope.launch {
        combine(_loadResult, _view, _query) { result, view, query -> /* 上と同じ */ }
            .collect { _state.value = it }
    }
}
```

これなら `state.value` 直読みが従来同様に最新を返す。`runTest` 下では `runCurrent()` が必要になる箇所が出るため、既存テストが落ちたら該当テストに `runCurrent()` を足す(テスト本体のアサーション意図は変えない)。**どちらの形にするかは「既存テスト群が無改修で緑か」を基準に決め、改修が要るなら最小の `runCurrent()` 追加に留める。**

- [ ] **Step 4: 全テスト**

Run: `./gradlew :frontend:allTests`
Expected: PASS

- [ ] **Step 5: コミット**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/InventoryViewModel.kt frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/inventory/InventoryViewModelTest.kt
git commit -m "refactor(frontend): InventoryViewModel の状態を combine+stateIn の単一合成に"
```

---

## Task 6: ShoppingListViewModel に OccurredAt を通す(4-7)

**Files:**
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/shopping/ShoppingListViewModel.kt`
- Modify: `frontend/src/commonTest/.../shopping/ShoppingListViewModelTest.kt`(`replenish` lambda シグネチャに OccurredAt を追加)
- Modify: `App.kt`(shopVm の `replenishStock` 配線と shopContent の `onReplenish`)

**背景:** ShoppingListViewModel の `replenishStock: (ProductId, Quantity, Note) -> ...` が OccurredAt を欠き、App.kt が `{ pid, q, n -> repository.replenish(pid, q, n, OccurredAt.now()) }` の `remember` クロージャで now を埋めている。Inventory/ProductDetail と対称に VM が OccurredAt を受ける形へ。

- [ ] **Step 1: VM のシグネチャに OccurredAt を追加**

`ShoppingListViewModel.kt`:

```kotlin
    private val replenishStock: suspend (ProductId, Quantity, Note, OccurredAt) -> RpcOutcome<Unit>,
```

`replenish` メソッド:

```kotlin
    suspend fun replenish(
        productId: ProductId,
        quantity: Quantity,
        note: Note,
        occurredAt: OccurredAt,
    ) = write(replenishStock(productId, quantity, note, occurredAt), UiText(Res.string.toast_replenished))
```

`import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt` を追加。

- [ ] **Step 2: 既存テストの lambda を更新**

`ShoppingListViewModelTest.kt` の `vm()` ヘルパー:

```kotlin
    replenish: suspend (ProductId, Quantity, Note, OccurredAt) -> RpcOutcome<Unit> = { _, _, _, _ -> RpcOutcome.Success(Unit) },
```

`import ...OccurredAt` を追加。`replenish` を直接呼ぶテストがあれば `OccurredAt` 引数を補う(現行 ShoppingListViewModelTest は replenish を直接呼んでいない=ヘルパー型のみ修正で足りる)。

- [ ] **Step 3: App.kt の配線を更新**

shopVm 生成(App.kt:280-291)の `replenishStock` を直接参照へ:

```kotlin
                                    replenishStock = repository::replenish,
```

shopContent の `onReplenish`(:364)で OccurredAt を call 境界から供給:

```kotlin
                                        onReplenish = { pid, q, n ->
                                            scope.launch { shopVm.replenish(pid, Quantity(q), Note(n), OccurredAt.now()) }
                                        },
```

`ShoppingListScreen` の `onReplenish` コールバックの引数型に日付要素が無い(クイック補充に picker 無し)ことを確認の上、`OccurredAt.now()` を呼び境界に置く。`OccurredAt`/`Quantity`/`Note` import は App.kt に既存。

- [ ] **Step 4: 全テスト + コンパイル**

Run: `./gradlew :frontend:allTests && ./gradlew :frontend:compileKotlinWasmJs`
Expected: PASS / BUILD SUCCESSFUL

- [ ] **Step 5: コミット**

```bash
git add frontend/src/
git commit -m "refactor(frontend): ShoppingListViewModel に OccurredAt を通し App の now 固定差し込みを解消"
```

---

## Task 7: VO バリデーションの runCatching を try/catch へ(4-12)

**Files:**
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/onboarding/OnboardingViewModel.kt`(:62-68, :87-92)
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/household/NeedHouseholdViewModel.kt`(:37-42, :97-99)
- Test(既存): OnboardingViewModelTest / NeedHouseholdViewModelTest(無改修で緑維持)

**背景:** VO 構築失敗の握り潰しを `runCatching{ Vo(x) }.getOrNull()` で行っている。nullable 原則の精神(不在を null で表さない)に整合させ、`try/catch(IllegalArgumentException)` の明示形へ。挙動は不変(失敗時に同じ分岐)。

- [ ] **Step 1: OnboardingViewModel の 2 箇所を try/catch 化**

DisplayName(:62-68):

```kotlin
        val displayName =
            try {
                DisplayName(current.name)
            } catch (_: IllegalArgumentException) {
                toast.show(errorText(RpcError.BadRequest("displayName", "invalid")))
                return
            }
```

HouseholdName(:87-92):

```kotlin
        val householdName =
            try {
                HouseholdName(rawHousehold)
            } catch (_: IllegalArgumentException) {
                toast.show(errorText(RpcError.BadRequest("householdName", "invalid")))
                _state.update { it.copy(submitting = false) }
                return
            }
```

VO のコンストラクタが `require(...)`(=`IllegalArgumentException`)で弾くことを確認(domain 規約: IAE 原則)。

- [ ] **Step 2: NeedHouseholdViewModel の 2 箇所を try/catch 化**

create(:37-42):

```kotlin
        val name =
            try {
                HouseholdName(rawName.trim())
            } catch (_: IllegalArgumentException) {
                toast.show(errorText(RpcError.BadRequest("householdName", "invalid")))
                return
            }
```

`parseCode`(:97-99)は呼び出し側が null 分岐(preview/join)で「不在=不正コード文言表示」に使うため、ここは「無効値=null」を返す純関数として spec 上は許容範囲だが、4-12 の精神に合わせ try/catch で明示しつつ戻り値型は維持できない(null 戻り)。**判断**: `parseCode` は「不正入力のプレビュー抑止」という UI 都合の述語的用途で、例外送出より null 返しが呼び出し側を素直にする。ここは **据置**とし、`create` の `runCatching` のみ try/catch 化する(4-12 の主眼は submit/create のバリデーション)。据置理由をコミットメッセージに明記。

- [ ] **Step 3: 全テスト**

Run: `./gradlew :frontend:allTests --tests "*OnboardingViewModelTest*" --tests "*NeedHouseholdViewModelTest*"`
Expected: PASS(無効名・有効名の分岐が不変)

- [ ] **Step 4: コミット**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/onboarding/OnboardingViewModel.kt frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/household/NeedHouseholdViewModel.kt
git commit -m "refactor(frontend): VO バリデーションの runCatching を try/catch に置換"
```

---

## Task 8: AuthState.Failed.message を UiText 化(4-11)

**Files:**
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/auth/AuthState.kt:21-23`
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/AuthViewModel.kt:78, 115`
- Modify: `frontend/src/webMain/.../App.kt:186`
- Modify: `frontend/src/commonMain/composeResources/values/strings.xml`
- Test(既存): AuthViewModelTest(Failed を生成するテストがあれば `.message` 比較を UiText 比較へ)

**背景:** `AuthState.Failed.message: String` に日本語文字列リテラル("ログインに失敗しました"/"起動に失敗しました")を直書き。strings 管理へ。Failed 表示色も他のエラー表示と統一(App.kt:186 の `AppText` に色指定が無い → `tokens.statusOut` を付与)。

- [ ] **Step 1: strings 追加**

`strings.xml` に追加:

```xml
    <string name="auth_failed_login">ログインに失敗しました</string>
    <string name="auth_failed_boot">起動に失敗しました</string>
```

- [ ] **Step 2: AuthState.Failed の型を UiText へ**

`AuthState.kt`:

```kotlin
import net.brightroom.mindstock.frontend.core.ui.UiText

    /** 失敗。message を表示し再ログイン可能に。 */
    data class Failed(
        val message: UiText,
    ) : AuthState
```

- [ ] **Step 3: AuthViewModel の 2 箇所を UiText に**

```kotlin
import net.brightroom.mindstock.frontend.core.ui.UiText
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.auth_failed_login
import mindstock.frontend.generated.resources.auth_failed_boot
```

:78 → `_state.value = AuthState.Failed(UiText(Res.string.auth_failed_login))`
:115 → `_state.value = AuthState.Failed(UiText(Res.string.auth_failed_boot))`

- [ ] **Step 4: App.kt の Failed 表示を resolve + 色**

App.kt:185-187:

```kotlin
                is AuthState.Failed -> {
                    AppText((state as AuthState.Failed).message.resolve(), color = MindstockTheme... )
                }
```

色は `LocalMindstockTokens.current.statusOut` を読む(`import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens`)。`resolve()` は `import net.brightroom.mindstock.frontend.core.ui.resolve`(既存 import あり)。実装:

```kotlin
                is AuthState.Failed -> {
                    val tokens = LocalMindstockTokens.current
                    AppText((state as AuthState.Failed).message.resolve(), color = tokens.statusOut)
                }
```

- [ ] **Step 5: 既存テスト更新 + 全テスト**

AuthViewModelTest / AuthViewModelSwitchTest が `AuthState.Failed("...")` を文字列で生成・比較していたら UiText へ更新(`shouldBeInstanceOf<AuthState.Failed>()` だけなら無改修)。

Run: `./gradlew :frontend:allTests && ./gradlew :frontend:compileKotlinWasmJs`
Expected: PASS / BUILD SUCCESSFUL

- [ ] **Step 6: コミット**

```bash
git add frontend/src/
git commit -m "refactor(frontend): AuthState.Failed.message を UiText 化し表示色を統一"
```

---

## Task 9: App.kt の Ready ブランチを関数分割(4-1 / 4-2 / 4-3 / 4-8)

**Files:**
- Modify: `frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/App.kt`(695 行 → 分割後も同ファイル内に private composable を配置)

**背景:** Ready ブランチ(:258-609 ≈ 350 行)に VM 生成・shell 配線・世帯シート・カタログオーバーレイ・商品詳細が一塊。private composable に分割し、ProductMasterViewModel 二重生成を解消、load+refresh の定型対を `LoadWithRefresh` に集約、`ProductSettingsSheetWithImage` の UI 状態を VM へ寄せる。**見た目・挙動はゼロ変更**(検証は dev server 実描画 eyeball)。

このタスクは TDD 不可(Compose レイアウトの単体テスト無し)。compile + 既存テスト + dev server 実描画で担保する。小さな安全のため 4 サブステップに分け、各サブステップ後に `compileKotlinWasmJs` を通す。

- [ ] **Step 1: `LoadWithRefresh` ヘルパーを導入(4-3)**

App.kt にファイル private composable を追加:

```kotlin
/** `load()` の初回実行と refresh シグナル購読での再 load をまとめる定型ヘルパー。 */
@Composable
private fun LoadWithRefresh(
    key: Any?,
    refresh: InventoryRefreshController,
    load: suspend () -> Unit,
) {
    LaunchedEffect(key) { load() }
    LaunchedEffect(refresh) { refresh.signal.collect { load() } }
}
```

既存の対(`LaunchedEffect(vm){vm.load()}` + `LaunchedEffect(refresh){refresh.signal.collect{vm.load()}}`)を置換:
- shopContent(:358-359) → `LoadWithRefresh(shopVm, refresh) { shopVm.load() }`
- activityContent(:369-370) → `LoadWithRefresh(activityVm, refresh) { activityVm.load() }`
- Master(:517-518) → `LoadWithRefresh(masterVm, refresh) { masterVm.load() }`
- Archived(:560-561) → `LoadWithRefresh(archVm, refresh) { archVm.load() }`

Run: `./gradlew :frontend:compileKotlinWasmJs` → BUILD SUCCESSFUL

- [ ] **Step 2: ProductMasterViewModel 二重生成を解消(4-2)**

Master ブランチ(:499-514)と Settings ブランチ(:573-588)で同一引数の `ProductMasterViewModel` を 2 回 `remember(householdId)` している。`when (catalogOverlay)` の **外側**(例えば `ReadyContent` 内、catalogOverlay を見る前)で単一の `productMasterVm = remember(householdId) { ProductMasterViewModel(...) }` を作り、Master/Settings 両ブランチで共有する。Settings が load を呼ばない点は許容(マスタープラン明記)。`masterVm`/`productSettingsVm` の 2 変数を 1 つに統合。

Run: `./gradlew :frontend:compileKotlinWasmJs` → BUILD SUCCESSFUL

- [ ] **Step 3: `ProductSettingsSheetWithImage` の状態を VM へ(4-8)**

現状(:628-683)Composable 側が `stored`(楽観表示)・`imageBusy`(再入抑止)を `remember` で持ち、`pickImage()`/`uploadImage`/`removeImage` を直接オーケストレーションしている。これを `ProductMasterViewModel` に移す:

- ProductMasterViewModel に画像状態フローを追加: `imageStored: StateFlow<Boolean>`(productId 単位)と `imageBusy: StateFlow<Boolean>`、`suspend fun pickAndUploadImage(productId)` / `suspend fun removeImageFor(productId)`(内部で `pickImage()`(expect/actual)→ `uploadImage`/`removeImage` → `stored`/`busy` 更新 → `invalidateImage`)。
- Composable は表示専任になり、`onPickImage = { scope.launch { viewModel.pickAndUploadImage(id) } }`、`onRemoveImage = { scope.launch { viewModel.removeImageFor(id) } }`、`image = rememberProductThumbnail(productId, viewModel.imageStored.collectAsState().value)`。

**注意:** `pickImage()` は `core/image` の expect/actual(webMain で実装)。VM は commonMain。`pickImage` を commonMain から呼べるか確認 — `core/image/ImagePicker.kt` が commonMain の `expect` 宣言なら VM から呼べる(webMain に actual)。**呼べない(webMain 限定)場合**は、picker 起動は Composable に残し、VM には「base64 を受けて upload + stored/busy 管理する」`suspend fun applyPickedImage(productId, base64)` と busy フラグだけを移す(picker 起動という platform 依存だけ Composable に残す)。この線引きを実装時に確認して決める。`imageBusy` の再入抑止は VM 側 `busy` フラグで担保。

この Step は影響範囲が `ProductSettingsSheetWithImage` と ProductMasterViewModel に閉じる。ProductMasterViewModel のコンストラクタ引数を変える場合(例: pickImage を渡す)は、テスト `ProductMasterViewModelTest` の `vm()` ヘルパーを追従更新する。**コンストラクタを変えない実装(pickImage を VM 内で expect 呼び)が可能ならそれを優先**(テスト無改修)。

Run: `./gradlew :frontend:compileKotlinWasmJs && ./gradlew :frontend:allTests` → 緑

- [ ] **Step 4: Ready ブランチを `ReadyContent`/`HouseholdSheets`/`CatalogOverlayContent` に関数分割(4-1)**

`AuthState.Ready ->` 内(:259-608)を 3 つの private composable に切り出す。VM 生成・wiring を各関数に同伴させる:

- `ReadyContent(...)`: householdId 解決後の主要部。homeVm/shopVm/activityVm/settingsVm 生成 + `AppShell`(stock/shop/activity/profile content)+ ProductDetailOverlay。引数で session 派生値(owner, sessionState, displayName, householdName, memberCount)・コントローラ(refresh/toast/reauth/scope)・repository 群・imageLoader・状態 hoist(opened/catalogOverlay/selectedTab)を受ける。
- `HouseholdSheets(...)`: HouseholdSwitcher + CreateHouseholdSheet + JoinCodeSheet(:394-425)+ settingsHhVm/settingsSheet。
- `CatalogOverlayContent(...)`: `when (catalogOverlay)`(:457-606)の AddProduct/Master/Archived/Settings。Step 2 の単一 productMasterVm を引数で受ける。

分割は機械的(コードを動かして引数で配線を渡すだけ)。状態 `var opened`/`var catalogOverlay`/`var selectedTab`/`var settingsSheet`/`var settingsStock` は呼び出し側(Ready ブランチ)に hoist し、各 content 関数へ value + setter ラムダで渡す。`CompositionLocalProvider(LocalProductImageLoader ...)` のスコープは維持。

**分割の指針:** 1 関数 150 行以下を目安。引数が 10 を超える関数は、関連する状態を小さな holder(例: `data class ReadyControllers(toast, reauth, refresh, scope)`)にまとめても良いが、YAGNI 優先で過剰な抽象は避ける。`activeHouseholdName`/`ProductSettingsSheetWithImage`/enum 群(NeedHouseholdSheet/SettingsSheet)は file private のまま維持。

Run: `./gradlew :frontend:compileKotlinWasmJs && ./gradlew :frontend:allTests` → BUILD SUCCESSFUL / 緑

- [ ] **Step 5: dev server 実描画で全画面 eyeball(忠実化非退行)**

手順は memory `fidelity-verify-loop-mechanics`。`./gradlew :frontend:wasmJsBrowserDevelopmentRun --continuous` で dev server 起動 → 在庫/買い物/活動/設定/商品詳細/補充消費シート/マスタ編集/商品追加/アーカイブ/世帯シート/オンボーディング/ウェルカムを描画し、PR #120/#119 マージ時の見た目から無変化であることを確認(本タスクは見た目ゼロ変更)。実機認証が要る画面(join/サイドバー)は描画範囲で確認できる範囲に留める。

- [ ] **Step 6: コミット**

```bash
git add frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/App.kt frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/catalog/ProductMasterViewModel.kt
git commit -m "refactor(frontend): App.kt の Ready ブランチを関数分割し VM 二重生成/定型 load を集約"
```

---

## 完了条件(フェーズ全体)

- [ ] `./gradlew :frontend:allTests` green
- [ ] `./gradlew :frontend:compileKotlinWasmJs` green
- [ ] `./gradlew build`(DB 不要範囲)green
- [ ] 削除シンボル(各 VM の `handleFailure`/`failWith`、散在 `SectionLabel`/アバター Box、`AuthState.Failed(String)`)の grep 残参照ゼロ
- [ ] dev server 実描画 eyeball で全画面の見た目が無変化(忠実化非退行)
- [ ] `.claude/rules/frontend-rpc-and-error.md` が FailureHandler を反映(R-8 追従)
- [ ] PR レビュー(subagent 二段 or インライン)

---

## Self-Review(spec 突き合わせ)

マスタープラン 4-1〜4-13 の被覆:
- 4-1 → Task 9 Step 4 / 4-2 → Task 9 Step 2 / 4-3 → Task 9 Step 1 / 4-8 → Task 9 Step 3
- 4-4 → Task 4 Step 1-5,8 / 4-5 → Task 4 Step 5(load 分類)+ Step 6(ArchivedScreen 色)
- 4-6 → Task 5 / 4-7 → Task 6
- 4-9 → Task 3 / 4-10 → Task 1 / 4-11 → Task 8 / 4-12 → Task 7 / 4-13 → Task 2

型/メソッド名整合: `FailureHandler.onMutationFailure`/`onLoadFailure`(全 VM・テストで一致)・`AvatarBadge(name,size,textSize)`・`SectionLabel(text,modifier,style)`・`LoadWithRefresh(key,refresh,load)`・`ShoppingListViewModel.replenish(...,occurredAt)`(VM/テスト/App.kt で一致)。

未確定で実装時に現物確認が要る分岐(プラン内に代替手順を明記済み):
- Task 1: `Font(...)` を `remember{}` 内で呼べるか → 不可なら CompositionLocal 化(Step 1-alt)
- Task 5: `stateIn` で `state.value` 直読みが既存テストで緑か → 落ちるなら init collect 方式(Step 3-alt)
- Task 9 Step 3: `pickImage()` を commonMain VM から呼べるか → 不可なら picker 起動のみ Composable 残し
