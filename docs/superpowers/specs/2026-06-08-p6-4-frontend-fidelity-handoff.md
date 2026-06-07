# P6-4 フロントエンド忠実化 セッション・ハンドオフ

別セッションへの引き継ぎ文書（2026-06-07〜08）。このセッションのやり取り・決定・実装・残課題を**漏れなく**記録する。メモリ `p6-4b-fidelity-program` / `full-replace-2026-06` の補完。新セッションは本書だけで再 litigation なしに続行できることを目標とする。

---

## 0. 経緯（ユーザの当初指摘）

`frontend/` を実起動した画面と デザインモック `docs/ref/mindstock.zip` に大きな乖離があり、ユーザから:
1. サインイン（サインアップ）画面が存在しない
2. 全体的にモバイル表示に寄りすぎ（モックはデスクトップ＝左サイドバー、スマホ＝ボトムナビ）
3. 総じてモック忠実度が達成されていない

→ **2 spec に分割**して対応（ユーザ承認）:
- **P6-4a** = アプリ chrome（ウェルカム splash + レスポンシブ左サイドバー + ボトムナビ + reflow 土台）
- **P6-4b** = スクショ駆動の画面別忠実度スイープ

その後ユーザは「**今治した画面も含めてまだ全然忠実じゃない箇所が結構いっぱいある**」「**勝手に判断して機能を削り、また同じ問答を繰り返すのをやめてほしい（正直呆れてる）**」と指摘。これを受けて忠実度の測り方と backend gap を一括確定した（§3, §4）。

---

## 1. デザインモックの扱い（重要・再現に必須）

- モック = `docs/ref/mindstock.zip` = **React/JSX プロトタイプ**（`app/*.jsx` + `lib/*.jsx` + 文字化け名の HTML）。`app/core.jsx`=デザイントークン(clay)/atom、`app/data.jsx`=シード/状態/ヘルパ(`relTime`/`predictDays`/`statusOf` 等)。
- **JSX を読むだけで「近い」判断するのは禁止**（過去の失敗原因）。**実描画して正解スクショを取る**こと。
- **モックの実描画手順**（このセッションで確立、`/tmp/mindstock_ref` は揮発なので再作成前提）:
  1. `unzip -p <zip> '*.html' > mock.html`（文字化け名を回避）、`app/`・`lib/` を個別 `unzip -o`。
  2. デバイス切替はモック内 `TweaksPanel`（`device: mobile|desktop`）= React state。**`app/app.jsx` の `TWEAK_DEFAULTS` の `"device"` を書き換えたコピーを 2 つ作る**（mobile/desktop）。
  3. `python3 -m http.server` で配信（CDN React/Babel + Google Fonts を読むので**ネット必須**、JSX は XHR 取得なので file:// 不可）。
  4. Playwright で各画面をスクショ。**iOS ベゼル（mobile）は合成ポインタを intercept する**ので、シート/詳細を開くクリックは desktop モックや `force=True`・role セレクタを使う。
- モック主要コンポーネントの所在:
  - `screens-a.jsx`: `Login`(splash) / `StockHome` / `SummaryStrip` / `ProductCard`
  - `screens-b.jsx`: `MoveSheet`(補充/消費・**DatePick あり**) / `BarcodeScanner`(カメラ)
  - `screens-c.jsx`: `ProductDetail` / `HistoryRow` / `CorrectionSheet` / `NotifSheet`
  - `screens-d.jsx`: 活動(Activity)タブ
  - `screens-master.jsx`: `MasterItemSheet`(商品マスタ編集・**ImageField あり**) / `MasterScreen` / `ArchivedScreen`
  - `screens-onboard.jsx`: `Onboard`(4 step) / `screens-household/join/invite.jsx`: 世帯系
  - `core.jsx`: `Sheet`/`Btn`/`Thumb`/`StatusDot`/`StockBar`/`Stepper`/`RoundBtn`/`Icon` + `TONES`(shadow sm/md/lg/pop・radius 12/16/22/28)
  - `app.jsx`: shell（`DesktopChrome`=248px サイドバー / `BottomNav`=浮遊ピル）/ `Login` 配線

---

## 2. このセッションで実装したもの（PR #119・ユーザが main へマージ予定）

ブランチ `feat/p6-4a-responsive-shell-welcome`。`compileKotlinWasmJs` + `wasmJsTest` 緑。コミット（古→新）:

```
6f66b3b shellKindFor 純関数(幅→shell種別)
f03cfb8 AuthState.Unauthenticated 追加・無言 redirect 廃止
012bd4c ウェルカム/サイドバー文言リソース
0017a56 WelcomeScreen(モック Login 準拠 splash)
a76f915 App.kt: 未認証→WelcomeScreen 配線(ボタンで Zitadel)
3c2df25 WideShell(248dp デスクトップ・サイドバー)
b5c6554 AppShell を幅で Wide/Compact 分岐 dispatcher 化
d5c909b App.kt: AppShell にサイドバー用 世帯名/表示名/スイッチャ配線
1194ab8 Ready 分岐の世帯ルックアップ重複を shellHousehold に統一
af3c13e アバター頭文字 fallback を文言リソース化(i18n)・WideShell 世帯前提 KDoc
2cf07c2 在庫詳細/補充消費/マスタ編集/戻る等をモック忠実化・デスクトップ調整(P6-4b 6点)
82aeb89 (chore) 誤コミットした lock/未追跡 doc を除外
d2e1fd5 商品詳細の履歴をモック挙動に忠実化(訂正畳み込み・理由・相対時刻)
3ffa6a0 (docs) P6-4b 差分監査
f3ea505 (docs) backend gap 4件 実装確定
```

### P6-4a（chrome）
- 新 `AuthState.Unauthenticated`。`AuthViewModel.boot()` の token==null を「即 `redirectToAuthorize()`」→「`Unauthenticated` に倒す」へ。`App.kt` で `WelcomeScreen(onSignIn={ scope.launch{ deps.redirectToAuthorize() } })`。
- 新 `app/welcome/WelcomeScreen.kt`（モック `Login` 準拠：ロゴ rotate-6°/タグライン/説明/チップ3/CTA busy/Zitadel 脚注）。**dev server 実描画でモック忠実を確認済**（未認証 cold state は backend 不要）。
- `shellKindFor(widthDp): ShellKind`（**840dp** 閾値・純関数・`ShellKindTest` 4 ケース）。`app/shell/AppShell.kt` を幅分岐 dispatcher 化。幅は `currentWindowAdaptiveInfo().windowSizeClass.minWidthDp`（`compose-adaptive` 既存依存。V2 へ deprecated だが warning のみ）。
- 新 `app/shell/WideShell.kt`（モック `DesktopChrome` 準拠 248dp サイドバー：ロゴ/世帯スイッチャ/商品追加/ナビ4/お知らせ(bell)/ユーザフッタ、content 中央寄せ max880dp。ブラウザ枠は再現しない）。狭幅は従来 Box+`BottomNav` 流用。
- **重要単純化**: AppShell は `AuthState.Ready`+非null householdId でのみ描画（`NeedHousehold` は shell 外の専用全画面）→ spec の `hasHousehold` 分岐は不要、WideShell は常に世帯ありで描く（KDoc に invariant 明記）。

### P6-4b（このセッションで着手した忠実化・eyeball 6点 + 履歴）
コミット `2cf07c2` と `d2e1fd5`。**全て dev server で mock vs 実装を実描画して確認済**:
1. デスクトップで在庫ヘッダの世帯ピル/ベルを非表示（サイドバーが担う）。新 `LocalIsWideShell`(staticCompositionLocalOf) を AppShell が provide → `InventoryRoute` が読んで `StockHomeScreen(wide=)` に渡す。
2. 在庫一覧をヘッダごと**画面全体スクロール**化（ヘッダを `LazyColumn`/`LazyVerticalGrid` の先頭 item に。検索 focus は header の安定 `key="header"` で維持）。
3. `ProductDetailScreen` をモック準拠に全面改修（中央サムネ72+名前 / 在庫カード：bigQty46・StatusDot label・最低在庫・StockLevelBar・補充soft/消費ghost・divider・wanted セクション / 履歴タイムライン）。
4. `MoveSheet` に商品サマリ箱 + 増減プレビュー（現在→後・マイナス在庫警告）追加。
5. `ProductSettingsSheet` を MasterItemSheet 準拠に（title「商品マスタの編集」・最低在庫を上下ボーダー化）。
6. 戻る/設定/お知らせ等の chrome ボタンを**角丸スクエア 新 atom `NavIconButton`**（44dp/radius13/border/surface/softShadow Sm）に。**円形 `RoundBtn` は `Stepper` の +/- と共有のため流用不可**→nav 用に別 atom 新設し ProductDetail/StockHome bell/catalog 3画面 back を移行。
7. **商品詳細の履歴をモック挙動に忠実化**（`d2e1fd5`、ユーザの具体指摘対応）:
   - **訂正を別行で積まない**：append-only な `StockMovement.Correction` を表示で畳み、対象の元行に「訂正済」+訂正後数量を出す（`byTarget = corrections.associateBy{it.target}`）。
   - **訂正理由を表示**（`Correction.reason`）。
   - **相対時刻を表示**：新 `feature/inventory/ui/RelTime.kt` の `relTimeOf(occurredAt, now): RelTime`（たった今/◯時間前/◯日前/M/D）+ `RelTimeTest`。文言は `history_time_*`。

### 新規/変更した主なファイル
- 新: `designsystem/atom/NavIconButton.kt`, `app/welcome/WelcomeScreen.kt`, `app/shell/WideShell.kt`, `app/shell/ShellKind.kt`(+`LocalIsWideShell`), `feature/inventory/ui/RelTime.kt`
- 変更: `app/shell/AppShell.kt`, `feature/inventory/ui/{ProductDetailScreen,MoveSheet,StockHomeScreen,InventoryRoute}.kt`, `feature/catalog/ui/{ProductSettingsSheet,AddProductScreen,ProductMasterScreen,ArchivedScreen}.kt`, `designsystem/theme/MindstockTokens.kt`(+`bg` トークン `#F6F2ED`), `composeResources/values/strings.xml`
- core/auth: `AuthState.kt`, `app/AuthViewModel.kt`
- テスト: `ShellKindTest.kt`, `RelTimeTest.kt`, `AuthViewModelTest.kt`(no_token→Unauthenticated)

---

## 3. 忠実度の測り方（合否基準・このセッションで確定）

- **主指標 = プロパティ適合チェックリスト**：モック JSX の**実数値**（size/weight/**lineHeight**/**letterSpacing**/色 hex/padding/gap/radius/shadow レベル/要素存在/挙動）を抽出し、実装が一致するか項目ごと ○/×。スコア=一致/全。**合格=対象 100% 一致**（例外=backend要/意図的省略 はフラグ付き除外）。「近い」で done にしない。
- **補助 = 寸法正規化 side-by-side + 差分ヒートマップ**：モックを端末ベゼル/ブラウザ枠抜きで実装と同寸 render し左右比較。pixel 差分は「注目箇所のヒートマップ」に使い**合否数値にはしない**。
- **pixel 一致率は基準にしない**：モック=HTML(ブラウザ描画)、実装=Compose(**js/wasmJs とも Skia 描画**、DOM でない)。フォント rasterize 差で生 pixel% は誤誘導。
- **デバッグ描画ループ = `:frontend:jsBrowserDevelopmentRun`（js）**（wasmJs は重い/OOM。js は軽い。どちらも Skia なので見た目は同等）。
- 原理的に一致不可（=トークン値に合わせ限界明記）：フォント rasterize 差・CSS 多層 box-shadow・`backdrop-blur`。

---

## 4. backend gap（4件・全て「実装する」で確定・各 spec→plan→別 PR）

全 RPC 面と突合した結果、mock 機能のうち backend 不足でフロントに反映できないのは**4件のみ**。**ユーザが全て実装で確定（2026-06-08）。後出し禁止**。

1. **occurredAt の設定（バックデート）** — `StockRegisterRpcService.replenish/consume/correct` がサーバ時刻固定。RPC に occurredAt 引数追加 → application Service → domain(`Stock.replenish/consume` が occurredAt 受ける) → infrastructure(datasource) 横断。frontend は MoveSheet 日時ピッカー復活・履歴は実日時表示。**過去の「occurredAt サーバ確定」決定を覆す**（該当 spec / `.claude/rules` 更新要）。
2. **商品画像（撮影/選択・表示）** — `ProductRegisterRpcService.changeImage(productId, ProductImage)` RPC と `ProductImage.Set(ImageRef)` は既存だが、**バイト列アップロード→ImageRef 生成** と **ImageRef→配信（表示）** の経路が無く E2E 不可。アップロード+配信基盤を作る。frontend は `Thumb` の画像表示化 + `ProductSettingsSheet`/`MasterItemSheet` に画像欄。
3. **消費予測「あと約X日」/ 予測トレンドバナー** — 消費ペース(daily rate)の概念がドメインに無い（mock は静的 seed `predictDays = qty/daily`）。**domain に消費履歴からのレート推定を実装**。在庫ホームの予測バナー・ProductDetail の「あと約X日」・shopping もこれ依存。
4. **通知（ベル/お知らせ/Web Push）** — 通知系 RPC/基盤が皆無。ベルは装飾、`NotifSheet` の中身も出せない。通知スキーマ+生成+Web Push を作り、ベル/NotifSheet を実配線。

**backend gap でない（=frontend で直す。私の手抜きだった）**:
- アバターの利用者別カラー（mock=`USERS` 別色 / 実装=accent 単色）→ resident id から色導出。
- 設定アイコン sliders（mock）vs 歯車（実装）等のアイコン差。

**その他の mock 機能は全部 backend 対応済**（検索/JAN lookup/採用/カスタム/単位/最低在庫/アーカイブ/wanted/世帯 create/rename/leave/changeRole/removeMember/createInvite/revokeInvite/join/list/previewInvite/履歴 history/活動 activity/オンボーディング register・whoami）= frontend で忠実化するだけ。

参考: 全 RPC 面は `rpc/src/commonMain/.../*RpcService.kt`（Resident/Catalog/Product/ProductRegister/Household/HouseholdRegister/Stock/StockRegister/Session）。

---

## 5. 全画面 差分監査（未完・継続要）

監査文書 = `docs/superpowers/specs/2026-06-08-p6-4b-fidelity-audit.md`。各画面の挙動差分・見た目差分を ○/× リスト化する方針。現状:
- **render 確認済**: WelcomeScreen / ProductDetail / MoveSheet / ProductSettingsSheet / StockHome(mobile/wide) / WideShell / BottomNav。
- **★未 render 監査（次セッションで実装を render→差分確定→修正）**: ShoppingList(買い物) / Activity(活動) / SettingsScreen(設定タブ) / AddProduct・ProductMaster・Archived(catalog) / Onboarding 4step / NeedHousehold・CreateHouseholdSheet・JoinCodeSheet・HouseholdSwitcher(世帯系)。
- **土台（最優先・未着手）**: `MindstockType`（`designsystem/theme/MindstockType.kt`）に **lineHeight も letterSpacing も入っていない** → 全テキストがモックのタイト組版より緩い（「今治した画面も含めて違う」の主因の一つ）。各スタイルに core.jsx 実数値で lineHeight/letterSpacing/weight を付与。影レベルも core.jsx 値へ寄せる。**ここを直すと全画面が一斉に寄る想定**。

---

## 6. ロードマップ（次セッション）

**トラック A：frontend 忠実化（新ブランチ。#119 マージ後の main 起点）**
1. 土台：`MindstockType` 実数値化（lineHeight/letterSpacing/weight）+ 影レベル。
2. 画面ごとに チェックリスト付け → 修正 → **js で mock vs 実装 同寸 A/B** → 100% 一致で done。未 render 監査画面（買い物/活動/設定/catalog/オンボ/世帯）を優先。
3. アバター利用者別カラー・アイコン差（sliders 等）も対応。

**トラック B：backend 4機能（各 spec→plan→別 PR、全て実装確定）**
- 既定の着手順：occurredAt → 消費予測 → 画像 → 通知（ユーザ指定あれば変更可）。
- いずれも brainstorm→spec→plan→Subagent-Driven or インライン実装、CLAUDE.md 4 原則・ドメイン方針踏襲。

---

## 7. 検証・実装 Tips（次セッション向け・このセッションで踏んだ事実）

- **render-verify 足場**：認証/backend 不要のプレビューは、`feature/inventory/ui/StockHomePreview.kt` の `previewStocks()`（committed・再利用可）+ **一時 `webMain/PreviewHarness.kt`**（各画面を sample state で mount）+ **Main.kt を `?preview=<name>` で分岐**（両方 **uncommitted で撤去**：`git checkout Main.kt` / `rm PreviewHarness.kt`）。dev server で `?preview=detail` 等を screenshot。**js dev server（`:frontend:jsBrowserDevelopmentRun`）を使う**。
- **dev server の port 8080 が居座る**ことがある（EADDRINUSE）→ `lsof -ti tcp:8080 | xargs kill -9` してから再起動。
- ビルド検証：`./gradlew :frontend:compileKotlinWasmJs`（フルビルドは OOM）。テスト：`:frontend:wasmJsTest` または軽い `:frontend:jsTest`（`--tests` フィルタ非対応・全体実行）。中間で webMain が一時非網羅になる場合は `compileCommonMainKotlinMetadata` を gate に。
- **commit は対象パスを明示**（`git add -A` で `.claude/scheduled_tasks.lock` と `docs/requirements-coverage-2026-06-07.md`（未追跡）を誤コミットした→`git rm --cached` で除外済）。
- ktlint `standard:filename`：単一 class のファイルは class 名と一致させる（`MovementTime.kt`→`RelTime.kt` で怒られた）。
- 規約：feature 層は `androidx.compose.material3.*` 直 import 禁止（`designsystem/atom` + `LocalMindstockTokens` 経由）。app/shell は adaptive/material3 直利用可。文言は `strings.xml`(ja) + `stringResource`。commonTest は kotlin.test `@Test` + Kotest assertions（FunSpec 不可）。commit メッセージに issue/PR 番号を書かない。
- ドメイン事実：`StockMovement`(sealed: Replenishment/Consumption/Correction) は `occurredAt: OccurredAt(LocalDateTime, JST)`・`actor: Resident`・`note: Note`、Correction は `target: MovementId` + `reason: Reason`。append-only（訂正は別イベント・net は畳み込み）。`TimeZone.JST` は `net.brightroom.mindstock.extensions.kotlinx.datetime.JST`（:shared）。

---

## 8. working agreement（最重要・厳守）

- モックの**挙動・見た目をそのまま再現**する。バックエンドで無理な所は**黙って落とさず必ず明示してユーザの判断を仰ぐ**。
- 「近い」で自己申告 done にしない。**必ず mock vs 実装を同寸 render して**プロパティ適合 100% を確認してから done。
- 過去 fidelity PR が「まだ違う」を繰り返したのは、JSX を読んで「近い」で判断し、モックと違う実装（日時ピッカー削除・訂正の別行積み上げ 等）を**黙って入れていた**ため。これを繰り返さない。

関連メモリ：`p6-4b-fidelity-program` / `full-replace-2026-06` / `frontend-visual-fidelity-expectation` / `subagent-vs-inline-frontend` / `local-build-tips` / `datetime-type-convention`。
