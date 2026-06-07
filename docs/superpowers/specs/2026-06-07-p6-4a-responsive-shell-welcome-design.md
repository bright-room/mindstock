# P6-4a: レスポンシブ・アプリ chrome ＋ ウェルカム splash 設計

## 背景と問題

`frontend/` を実起動した画面とデザインモック（`docs/ref/mindstock.zip`）に大きな乖離があり、ユーザから3点の指摘:

1. **サインイン（サインアップ）画面が存在しない**
2. **全体的にモバイル表示に寄りすぎ**。モックはデスクトップで左サイドバー、スマホでボトムナビと切り替わるが、実装はボトムナビ固定。
3. **総じてモック忠実度が達成されていない**

モックを React/JSX ソースのまま読むのではなく、**実レンダリングしてスクリーンショット**で確認した結果、根本原因を特定:

- **splash 欠落（指摘1）**: `AuthViewModel.boot()` は `token == null` のとき *即座に* `redirectToAuthorize()` する（`app/AuthViewModel.kt:84-86`）。ユーザはウェルカム画面を一切見ない。モックには `Login`（ロゴ+タグライン+「ログインして始める」→Zitadel）の splash が存在する。
- **レスポンシブ欠落（指摘2）**: `AppShell` は単一 `Box` + `BottomNav` で幅分岐ゼロ（`app/shell/AppShell.kt`）。モックは `DesktopChrome`（248px 左サイドバー）と浮遊 `BottomNav` ピルを幅で切り替える。デスクトップ shell が完全に欠落。
- **忠実度（指摘3）**: 上記2つの構造的欠落が支配的。加えて画面ごとの細部差は、これまで JSX ソース読みで進めたため不可視だった。

## スコープ分割

過去に「fidelity」系 PR（P6-1a visual-fidelity, eyeball-fidelity ×2）を重ねても「まだ違う」と言われた。差別化は**方法**: モックを実描画した画像を正解として差分を取る。作業は2 spec に分割（ユーザ承認済み）:

- **Spec1（本書 = P6-4a）**: アプリ chrome（ウェルカム splash + レスポンシブ左サイドバー + ボトムナビ整備 + 各画面の reflow 土台）。**構造を先に正す。**
- **Spec2（後続 = P6-4b）**: スクショ駆動の画面別忠実度スイープ。現状実装を dev server で実描画し、モック画像と画面ごとに差分を取る。

本書は Spec1 のみを扱う。

## 忠実化ターゲット（モック実描画）

`docs/ref/mindstock.zip` を HTTP サーバ + Playwright で実描画し、両デバイスモード（モック内 `TweaksPanel` の device トグル: mobile/desktop）で全画面をスクリーンショット取得済み。Spec1 が直接の正解とする画像:

- **デスクトップ在庫ホーム**: 248px 左サイドバー（ロゴ / 世帯スイッチャ / 商品を追加 / ナビ4項目 / spacer / お知らせ / ユーザフッタ）＋ content 中央寄せ。ブラウザ枠（信号機ドット + `mindstock.app`）は **モックのプレゼン足場であり再現しない**（実ブラウザがそれ）。
- **モバイル在庫ホーム**: 浮遊グラスピル・ボトムナビ（在庫 / 買い物 / [+FAB] / 履歴 / 設定）。
- **サインイン splash（mobile/desktop）**: ロゴ（box アイコン rotate -6°）+ "mindstock" + タグライン「暮らしの在庫を、ちょうどよく。」+ 説明文 + チップ3つ（買い忘れ防止 / 消費予測 / 家族で共有）+「ログインして始める」ボタン + 脚注「Zitadel アカウントで安全にサインイン」。

## 方式の選択（レスポンシブ機構）

- **(A) 自前レスポンシブ shell ＋ size-class 判定 〔採用〕** — `currentWindowAdaptiveInfo().windowSizeClass`（既存依存 `compose-adaptive`）で幅を採り、chrome（サイドバー/浮遊ピル）は自前描画。モックの独自 chrome（世帯スイッチャ・追加ボタン・プロフィールフッタ・グラスピル）に忠実化でき、`app/shell/` 層なので adaptive API 直接利用も `frontend-designsystem.md` 規約で許容。
- (B) `NavigationSuiteScaffold` — 記述は減るが標準 NavigationBar/Rail/Drawer で、clay モックの独自 chrome に合わず忠実度が崩れる。**却下**。
- (C) `BoxWithConstraints` で素の幅判定 — 動くが既存の size-class 依存を使わず車輪の再発明。**却下**。

## 設計

### 1. ブレークポイント

純関数に切り出す（テスト可能性のため。UI 描画網羅は追わない＝規約準拠）:

```kotlin
enum class ShellKind { Compact, Wide }

fun shellKindFor(widthDp: Int): ShellKind =
    if (widthDp >= 840) ShellKind.Wide else ShellKind.Compact
```

- `840dp` = Material3 Expanded 標準閾値（ユーザ承認済み）。
- タブレット縦（600–840 Medium）は Compact（ボトムナビ）扱い。モックは2状態のみなので2分岐で十分。
- 呼び出し側は `currentWindowAdaptiveInfo()` から幅を採って `shellKindFor` に渡す。

### 2. `AppShell` の分岐

現状: `AppShell(selectedTab, onSelectTab, onAdd, stockContent, shopContent, activityContent, profileContent)`。

変更後: `AppShell` が**分岐器**となり、現行の引数に**サイドバー描画用の引数を追加**した上で、内部で `shellKindFor` により Wide 経路（`WideShell`）/ Compact 経路に分岐する。

追加引数:

- `householdName: String`（サイドバーのスイッチャ/フッタ表示）
- `displayName: String`（フッタのユーザ名）
- `hasHousehold: Boolean`（世帯なし時は「商品を追加」とナビの一部を出さない／スイッチャ文言を変える。モック `DesktopChrome` と同じ挙動）
- `onOpenSwitcher: () -> Unit`
- `onBell: () -> Unit`（present だが Spec1 では no-op 配線で渡す。bell=通知は将来）

content 4 つは両モード共通。`CompactShell` は現行 `AppShell` の中身をほぼそのまま移設（`Box` + `BottomNav` + bottom padding 88dp）。

**content の中央寄せ reflow 土台**: 両モードとも content を中央寄せスクロールコンテナに入れる。

- Wide: `maxWidth ≈ 880dp`、padding 34dp（縦）/40dp（横）（モック `scrollPad`/`maxWidth` 準拠）。
- Compact: 現行（横 18dp 等、ボトムナビ分の下 padding）。

これは「器」だけ用意する土台であり、各画面**内部**の列数（grid 3 カラム等）や横長カード化は **Spec2 送り**。

### 3. `WideShell`（デスクトップ・サイドバー）

モック `DesktopChrome`（`app/app.jsx:206-267`）準拠。`Row { Sidebar(248dp) ; Content(weight 1) }`。

サイドバー（248dp 固定・右境界線 `lineSoft`・背景 `surface`・padding 24/16、上から）:

1. **ロゴ行**: box アイコン（accent 背景, `onAccent`, rotate -6°）+ "mindstock"（800 weight）。
2. **世帯スイッチャボタン**: 家アイコン + 世帯名（`hasHousehold ? householdName : "世帯がありません"`）+ サブ文言（`切り替え・追加` / `つくる / 参加する`）+ chevron。押下で `onOpenSwitcher`。
3. **商品を追加ボタン**（`hasHousehold` 時のみ）: PrimaryButton 相当、押下で `onAdd`。
4. **ナビ項目**: `Tab` enum を流用（在庫 / 買い物 / 履歴 / 設定）。active は `accentSoft` 背景 + `accent` 文字。`hasHousehold==false` 時は設定のみ（モックと同じ）。
5. spacer（`weight 1`）。
6. **お知らせ（bell）**（`hasHousehold` 時のみ）: アイコン + 「お知らせ」。押下で `onBell`（Spec1 は no-op）。
7. **ユーザフッタ**: 上境界線 + アバター（表示名頭文字）+ 表示名 + 世帯名（`hasHousehold ? householdName : "世帯なし"`）。

content 領域: 右側スクロール、`maxWidth 880dp` 中央寄せ。

中央 +FAB はサイドバーには無い（追加は専用ボタンが担う）。`Tab` enum の中央 FAB 概念はボトムナビ専用。

実装は `app/shell/WideShell.kt`（新規）。atom（`AppIcon`/`AppText`/`PrimaryButton`）+ `LocalMindstockTokens` で組む。

### 4. `CompactShell`（モバイル）

現行 `AppShell` の本体（`Box` + `BottomNav` + bottom padding 88dp）を Compact 経路として `AppShell` 内にインラインで残す（最小変更）。`BottomNav` は現行流用（変更なし）。content の中央寄せコンテナ化のみ反映。

### 5. ウェルカム / サインイン splash

**AuthState 拡張**: `core/auth/AuthState.kt` に `Unauthenticated`（object）を追加（ユーザ承認済み命名）。`Booting`/`NeedOnboarding`/`NeedHousehold`/`Ready`/`Failed` と並ぶ中立な技術名。

**boot 変更**: `app/AuthViewModel.kt:84-86`

```kotlin
val token = deps.loadValidToken()
if (token == null) {
    _state.value = AuthState.Unauthenticated   // 旧: deps.redirectToAuthorize()
    return
}
```

`/auth/callback` 処理（line 77-82）と近日失効トークンの先回り redirect（`ReauthController` 経由）は**変更しない**。splash は「真に token が無い cold 状態 = 初回訪問/ログアウト後」のみに出る。

**WelcomeScreen**（新規。`feature/onboarding/ui/WelcomeScreen.kt` か `app/welcome/WelcomeScreen.kt`。auth coordinator が `app/` にあるので `app/welcome/` に置く）:

モック `Login`（`app/screens-a.jsx:3-47`）準拠:

- ロゴ（box アイコン rotate -6° + "mindstock" 30px 800）+ タグライン「暮らしの在庫を、ちょうどよく。」
- 説明文「買い忘れも、買い過ぎも減らす。日用品のストックを家族とゆるやかに共有する在庫ノート。」
- チップ3つ（買い忘れ防止 / 消費予測 / 家族で共有）
- 「ログインして始める」ボタン（押下で busy → `vm.redirectToAuthorize()`。busy 時「Zitadel に接続中…」）
- 脚注「Zitadel アカウントで安全にサインイン」
- 「招待リンクを開く（デモ）」は**モック専用なので Spec1 では省略**（join はディープリンク経由で別途）。

レイアウト: `shellKindFor` で幅判定し中央寄せ（Compact は縦並び全幅、Wide はモック desktop-login 準拠の中央寄せカード/全幅）。

**App.kt 配線**: `state` の `when` に `AuthState.Unauthenticated -> WelcomeScreen(onSignIn = { scope.launch { vm.redirectToAuthorize() } })` を追加。

**文言**: 全てユーザ向けなので `commonMain/composeResources/values/strings.xml`（ja）へ追加し `stringResource` 参照（`frontend-i18n-and-font.md` 規約）。

### 6. overlays / sheets の扱い（Spec2 との境界）

モック `app.jsx` では sheets（`MoveSheet`/`ProductDetail`/`AddProduct`/`HouseholdSwitcher` 等）が desktop/mobile 分岐の**外側**で共通描画される（`app.jsx:186-199`）。実装も App.kt で AppShell の外側に共通描画されており、これを**両モード共通のまま維持**する。

desktop で bottom-sheet が全幅で出るのをモーダル中央化すべきか等の**見た目調整は Spec2 送り**（advisor 指摘の典型的ミス箇所。Spec1 では現行流用に留め、Spec2 で実描画して判断）。

## テスト

`frontend-compose-conventions.md` 準拠（commonTest は kotlin.test `@Test` + Kotest assertions、UI 描画網羅は追わない）:

1. **`shellKindFor` 純関数**: `839 → Compact`, `840 → Wide`, 大きい値 → Wide。
2. **`boot()` の `token==null` 分岐**: 既存 `AuthViewModelTest` に「token 無し → `state == Unauthenticated`（旧: redirect 副作用）」を追加。`/auth/callback` パス・有効 token の既存分岐が壊れないことも確認。

## スコープ外（Spec2 = P6-4b 送り）

- 各画面**内部**の desktop reflow 微調整（grid 3 カラム・横長カード・content 幅最適化の画面別チューニング）。Spec1 は中央寄せコンテナの土台のみ。
- desktop でのシート/オーバーレイの見た目（全幅 bottom-sheet をモーダル中央化すべきか）。
- bell/通知の中身（Spec1 は present-but-no-op）。
- join ディープリンク（モック `Login` の「招待リンク」導線）。
- 画面ごとのモック差分潰し全般（Spec2 のスクショ駆動スイープが本体）。

## 受け入れ条件

- 幅 ≥ 840dp のブラウザで起動すると 248px 左サイドバー shell が出る。サイドバーのナビ4項目でタブ遷移でき、active が `accentSoft` でハイライトされる。商品を追加 / 世帯スイッチャが機能する。
- 幅 < 840dp では従来の浮遊ボトムナビが出る（リサイズで動的に切り替わる）。
- 未認証（token 無し）で起動するとモック `Login` 準拠のウェルカム splash が出る。「ログインして始める」で Zitadel へ redirect する（従来の無言 redirect は廃止）。
- `./gradlew :frontend:compileKotlinWasmJs` が通る。`shellKindFor` と boot 分岐のテストが緑。
