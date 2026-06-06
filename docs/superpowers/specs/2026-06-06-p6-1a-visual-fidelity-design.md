# P6-1a 見た目忠実化 + デザインシステム整備 設計

- 日付: 2026-06-06
- 対象モジュール: `:frontend`(`designsystem/` / `feature/inventory/`)
- 参照モック: `docs/ref/mindstock.zip`(`app/core.jsx` の token/atom、`app/screens-a.jsx` の StockHome)
- 関連: [P6-1 在庫 frontend 設計](2026-06-06-p6-1-inventory-frontend-design.md) / rule [frontend-designsystem](../../../.claude/rules/frontend-designsystem.md)

## 背景と問題

P6-1a で在庫スパイン(PR #111、origin/main にマージ済み)を実装したが、見た目がモックから大きく乖離している。

**重要(実コード調査で判明)**: 機能と画面構造は**既に揃っている**。`App.kt` → `AppShell` →
`InventoryRoute` → `StockHomeScreen` / `ProductCard` / `MoveSheet` / `ProductDetailScreen` が
**完全に配線済み**で、起動すれば在庫一覧・検索・補充/消費シート・詳細・トーストまで動く。
atom も `designsystem/atom/` に一通り存在する(`PrimaryButton`/`Thumb`/`StatusDot`/`StockLevelBar`/
`SegmentedControl`/`TextInput`/`Sheet`/`Stepper`/`RoundBtn`/`Toast`/`AppText`/`AppIcon`)。

問題は**すべてが素の Material3 ラッパで視覚的に作り込まれていない**こと:
- `AppText` は `style` 引数すら持たず、全テキストが Material 既定サイズ/ウェイト(モックの 800/25sp 見出し等が出ない)
- `ProductCard` は surface 背景・枠線・影・角丸が無い素の `Column`(カードに見えない)
- `PrimaryButton` は補充/消費とも同一の primary `Button`(soft/ghost variant もアイコンも無し)
- `Thumb` は単色 Box(ハッチング無し)、`StatusDot` は 8dp 円のみ(soft リング/ラベル無し)、
  `StockLevelBar` は素の `LinearProgressIndicator`(min 閾値マーカー無し)、`Sheet`/`Toast` も Material 既定
- モックにある **SummaryStrip(買い物 CTA + 消費予測)・挨拶上部のヘッダ chrome(世帯ピル + ベル)が未実装**
- token に**影スケールが無い**(`MindstockTokens` は status色 + 半径のみ)

**診断**: モックの「ちょうどよさ」は画面構造より `core.jsx` の **token と atom** に宿っている。
素の atom ラッパこそが「ダサさ」の本体。**配線は不要**で、token → atom の忠実度アップグレード →
不足分(SummaryStrip / ヘッダ)の追加 → 画面再構成、の順に直せば StockHome と後続画面が見た目を自動継承する。

## ゴール / 非ゴール

### ゴール
1. **Token 層**を拡張し(影スケール / 寸法をモック実数値で)、忠実度の土台を作る。
2. **Atom ライブラリ**を `core.jsx` 相当まで忠実に再構築する(後続画面も見据えて広く)。
3. **StockHome**をモック `screens-a.jsx` に忠実化する(P6-1a の proof-of-fidelity)。
4. **検証ループ**(dev server + スクショ vs モック横並び)を確立する。

### 非ゴール(スコープ柵)
- indigo / pine トーン、角丸 variant、desktop サイドバー chrome、tweaks パネル
  → モック検証用ハーネスでありアプリ本体ではない。**作らない**(clay + mobile のみ)。
- 買い物 / 履歴 / 設定タブ、各種 Sheet の**画面実装**は本パスの対象外
  (ただし後続が即組めるよう atom は先に整備する)。
- 消費予測の実ロジック、手動 wanted フラグの配線(= P6-1b)。本パスでは暫定/非表示。

## ユーザ確認済みの決定

| 論点 | 決定 |
| --- | --- |
| アイコン | 重要分(ロゴ箱 + 商品カテゴリ drop/paper/egg/bottle/salt/bolt/box)を ImageVector 移植、nav/汎用は material-icons-extended のまま。すべて `AppIconName` 経由で seam 維持 |
| SummaryStrip | 見た目だけ実装。消費予測「あと約N日」と wanted バッジはデータが無い(domain 未提供)ため暫定/非表示。切らし/残りわずか件数は `Stock.status()` から実値 |
| atom 整備範囲 | 後続も見据えて広く(Sheet/Stepper/RoundBtn/Thumb/Seg/Btn 全 variant 等を一通り) |

## アーキテクチャ

層責務・依存方向は既存規約([frontend-architecture](../../../.claude/rules/frontend-architecture.md) /
[frontend-designsystem](../../../.claude/rules/frontend-designsystem.md))を踏襲する。

- **token → atom → screen の一方向**。feature 層は `androidx.compose.material3.*` を直接 import せず、
  `designsystem/atom` の atom と `MindstockTheme` 経由でのみ UI を組む。
- Material3 に無いモック固有トークン(status色・影・半径・寸法)は `LocalMindstockTokens`(CompositionLocal)で供給。
- 表示変換(domain の `Stocks` → 画面表示モデル)は feature 内の薄い presentation mapper に置き、ViewModel は薄く保つ。

### ① Token 層(`designsystem/theme`)

`MindstockTokens` に以下を追加する。

- **影スケール** `shadowSm / shadowMd / shadowLg / shadowPop`。
  - 罠: モックの影は CSS の多層 box-shadow。Compose の `Modifier.shadow` は単一 elevation で、
    elevation 数値を素直に当てると平板に見える(ダサさの主因の一つ)。
  - 対策: token ごとに調整した **`Modifier.softShadow(level)` ヘルパ**を `designsystem` に 1 つ用意し、
    近似(elevation + ambient/spot color の調整、必要なら 2 枚重ね)を**一点に封じ込める**。
    atom はこのヘルパだけを使い、生の `Modifier.shadow` を散らさない。
- **半径**: 既存(sm12/md16/lg22/xl28)を踏襲。
- **色**: clay の OKLCH→sRGB は既にハッシュ済みだが、surface/line/accent の数点を実コンバータで
  再検算してから採用する(オフ hue は安っぽく見える)。
- **寸法**: token 化はせず、各 atom にモックの実数値(card padding 18dp、gap、font サイズ/weight)を**直値**で持たせる。
  「だいたい」で寄せない。
- **タイポグラフィ プリセット** `MindstockType`(`designsystem/theme`): モックで頻出する text style
  (見出し 800/25sp、カード見出し 600/15.5sp、サブ 500/13sp、大数字 700/30sp tnum 等)を `TextStyle` プリセットとして定義し、
  `AppText(style = ...)` で参照する。Material3 `Typography`(NotoSansJP 適用済み)を土台に必要な weight/size/letterSpacing を上書き。

### ② Atom ライブラリ(`designsystem/atom`)

**既存 atom を忠実度アップグレードする(新規作成ではなく置換/強化)**。`core.jsx` の実数値・配色・ウェイトに合わせる。
各 atom は state hoisting(状態は引数+コールバック)に従う。表の「状態」列が現状。

| atom | 状態 | モック対応 | 要点 |
| --- | --- | --- | --- |
| `AppText` | 既存(style 無し) | 全テキスト | **`style: TextStyle` / `color` 引数を追加**。モックの font(weight/size/letterSpacing)を表現できるよう、`MindstockType`(後述)のプリセットを受け取れるようにする。これが最優先(見出しが効かない主因) |
| `AppButton`(現 `PrimaryButton` を拡張) | 既存(素 Button) | `Btn` | variant: primary/soft/ghost/quiet/danger、size: sm/md/lg、icon、押下 scale アニメ(`animateFloatAsState`)。既存呼び出し側(ProductCard/MoveSheet/ProductDetail)の互換を保つ |
| `Thumb` | 既存(単色 Box) | `Thumb` | ハッチ背景(45°ストライプ、`Canvas`/`drawBehind`)+ アイコン or 画像。`ProductImage` が URL を持てば画像、無ければカテゴリアイコン |
| `StatusDot` | 既存(円のみ) | `StatusDot` | ドット + soft リング(box-shadow 相当を二重円で)+ 任意ラベル(`withLabel`) |
| `StockLevelBar` | 既存(素 LinearProgress) | `StockBar` | fill(status色)+ min 閾値マーカー + 幅アニメ(`animateFloatAsState`)。`Canvas` で自前描画。comfortable = max(min*2, min+3, qty, 1) |
| `SegmentedControl` | 既存(素 Seg) | `Seg` | active タブに surface + 影、トラック背景、アイコン対応に忠実化 |
| `Stepper` | 既存(素) | `Stepper` | 大数字(52sp、tnum)+ `RoundBtn` ± 、bump アニメ |
| `RoundBtn` | 既存(素 IconButton) | `RoundBtn` | 58dp 丸ボタン、押下 scale、accent 変種 |
| `Sheet` | 既存(ModalBottomSheet) | `Sheet` | ハンドル + タイトルのトーンを忠実化(ModalBottomSheet の container/角丸/handle を token に合わせる) |
| `Toast` | 既存(素 Snackbar) | `Toast` | ink 背景 + アイコン丸 + 角丸、モックのトーンに |
| `SearchField`(新規 or `TextInput` 強化) | `TextInput` 既存 | StockHome 内 input | 検索アイコン + クリアボタン、focus 時 accent ボーダー、高さ 50dp・角丸 14dp |
| `HouseholdPill`(新規) | 無し | `HouseholdPill` | 世帯名 + メンバー数、タップで切替(本パスは表示のみ・タップ動作は後続) |
| `AddTile`(新規) | 無し | StockHome 内 dashed button | 破線枠の「商品を追加」タイル |

新規追加は `SearchField` / `HouseholdPill` / `AddTile` の 3 つのみ。残りは既存ファイルの中身を差し替える。

`AppIcon` 拡張:
- `AppIconName` enum に商品カテゴリ/ロゴ(box ロゴ・drop・paper・egg・bottle・salt・bolt・leaf)を追加し、
  `core.jsx` の SVG パスを **Compose `ImageVector`(`materialIcon`/`ImageVector.Builder`)に機械移植**。
- nav/汎用(cart・plus・minus・clock・home・user・bell・search・chevR/L/D・x・check・grid・list・trend 等)は
  material-icons-extended のまま `vector()` で対応付け。
- feature は引き続き `AppIconName` でのみ参照(差し替え seam 維持、designsystem 規約に整合)。

### ③ StockHome 忠実化(`feature/inventory/ui`)

モック `screens-a.jsx` の `StockHome` を再現する。構成(上から):

1. ヘッダ行: 世帯ピル(`HouseholdPill`)+ ベルボタン(未読バッジ付き)
2. 挨拶 + 見出し: 「こんにちは、〇〇」+「在庫」(800/25sp)
3. **SummaryStrip**: 買い物 CTA カード(need 件数で accent/surface 切替)+ 消費予測ストリップ
4. `SearchField`(在庫を名前で検索 / クライアント側フィルタ)
5. 件数表示 +`SegmentedControl`(grid/list)
6. 本体: list=`ProductCard` / grid=`CompactCard` の LazyVerticalGrid or LazyColumn + 末尾 `AddTile`
7. 検索ヒット 0 件の空状態(アイコン + 文言 + 追加ボタン)

`StockHomeScreen` / `ProductCard` は**既存**(restyle)。`CompactCard`(grid 用)・`SummaryStrip`・ヘッダ chrome は**新規追加**。
`App.kt` → `InventoryRoute` → `StockHomeScreen` の配線は既に通っているので**配線変更は不要**(起動すれば画面は出る)。
`MoveSheet` / `ProductDetailScreen` も既存で、アップグレードした atom(Sheet/Stepper/RoundBtn/Btn/StatusDot/StockLevelBar)を
自動継承する。本パスでは StockHome を主対象にしつつ、これらに残る素 Material3 直書きがあれば atom 経由に寄せる。

**データの扱い(presentation mapper)**:
- `Stock.status()`(十分/残りわずか/在庫切れ)→ StatusDot 色・StockBar 色。実値。
- 数量 `currentQuantity()`、単位 `product.setting.unit`、名前 `product.name`、最小 `minimumStock`。実値。
- サムネアイコン: `Product` は category/icon を持たない。**feature 側の名前ヒューリスティック + 既定 `box`** で解決
  (`ProductImage` が URL を持てば画像優先)。将来 domain 提供時に差し替え。
- SummaryStrip の切らし/残りわずか件数: `status()` の集計で実値。
- 消費予測「あと約N日」/ wanted バッジ/「自分で追加」: daily 消費率・wanted は domain 未提供のため
  **本パスでは非表示 or プレースホルダ**(P6-1b で配線)。SummaryStrip のレイアウト/配色自体はモック忠実に作る。

ViewModel/UiState は現状(`InventoryUiState.Content(stocks, view)`)を踏襲。クライアント検索クエリは
画面ローカル `remember`(一時 UI 状態)で保持し ViewModel に持たせない。

### ④ 検証ループ(最重要)

前回ダサかった主因はおそらく**モックとの視覚照合を一度もしなかった**こと。spec に手順を組み込む:

1. **着手前**に `./gradlew :frontend:wasmJsBrowserDevelopmentRun` で dev server が起動することを実証する。
   (メモリの「WasmJs OOM」は**フルビルド**の話で dev server は別経路。依存する前に確認)
2. atom / StockHome 実装後、dev server を起動し **Playwright(webapp-testing スキル)でスクショ**を撮る。
3. ブラウザで開いたモック(`docs/ref/mindstock.zip` 展開)と**横並び比較**し、ズレ(余白・色・weight・影)を是正する。
4. 「実装した」ではなく「モックと並べて一致を確認した」まで到達して完了とする。

## エラー処理

本パスは表示の忠実化が主眼で新しい RPC 経路を増やさない。既存の `InventoryUiState.Loading/Error/Content`
分岐(`userMessageOf` 経由の文言化)を踏襲する。Loading/Error も素テキストではなくモックの空/読込トーンに寄せる
(軽量で可)。

## テスト

[frontend-compose-conventions](../../../.claude/rules/frontend-compose-conventions.md) 準拠。
commonTest は **Kotest FunSpec 不可**、`kotlin.test.@Test` + Kotest assertions。

- UI 描画網羅は追わない(視覚は検証ループで担保)。
- 検証可能ロジックのみテスト: `Stock.status()` → 色/ラベルのマッピング、SummaryStrip の件数導出、
  StockBar の comfortable / 充填率計算、検索フィルタ。

## 文言 / フォント

[frontend-i18n-and-font](../../../.claude/rules/frontend-i18n-and-font.md) 準拠。新規ユーザ向け文言は
`commonMain/composeResources/values/strings.xml`(ja)に追加し `stringResource` 参照。フォントは NotoSansJP を踏襲。

## 作業順序(概略)

1. dev server 起動の実証 + モック展開・ブラウザ表示の準備。
2. Token 層拡張(影ヘルパ・色再検算)。
3. Atom 忠実再構築(Btn/Thumb/StatusDot/StockBar/Seg/Stepper/RoundBtn/BottomSheet/SearchField/HouseholdPill/AddTile)+ AppIcon 拡張。
4. ProductCard/CompactCard + StockHome 再構築。
5. 文言追加・ロジックテスト。
6. 検証ループ(スクショ横並び)→ 是正 → 完了判定。

詳細なステップ分解は本 spec 承認後に実装プラン(writing-plans)で行う。
