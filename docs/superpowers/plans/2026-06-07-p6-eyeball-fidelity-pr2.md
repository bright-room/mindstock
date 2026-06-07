# P6 実機 eyeball 忠実化 PR2 実装プラン

> **For agentic workers:** 実装は superpowers:executing-plans でタスク単位に進める。本 PR は仕様確定済みの機械的フロント作業のため、インライン実装+稼働中 dev server での実機スクショ確認で進める([[subagent-vs-inline-frontend]])。

**ゴール:** 実機 eyeball で残った忠実度の問題(#3 買い物/履歴タブが素のプレースホルダ・#4 下部ナビが Material 既定)をモック(`docs/ref/mindstock.zip`)に寄せ、軽微な不活性配線も直す。

**方針:** モック JSX(`screens-c.jsx` 買い物 / `screens-d.jsx` 履歴 / `app.jsx` BottomNav)を基準に clay 忠実化。Compose/Wasm に真の backdrop-blur は無いので下部ナビは半透明浮遊ピルで近似(ユーザ承認済)。feature 層は `LocalMindstockTokens` のみ参照(material3 直 import 禁止)、app/shell は material3 可。

**検証:** 稼働中 dev server(:8080)+ headed Chromium(CDP :9222)でモック横並びスクショ。ロジック追加は最小(メンバー stale 修正のみ VM テスト)。

---

### Task 1: 共通文言と EmptyState 整理

**Files:** `frontend/src/commonMain/composeResources/values/strings.xml`

- [ ] 買い物の進捗バナー文言 `shop_progress`(「あと %d 点で買い物完了」)を確認/追加、`shop_progress_count`(「%d/%d」)を確認。既存があれば流用。
- [ ] 消費シート用メモ placeholder `move_note_consume_placeholder`(例「メモ(任意)」)を追加(補充は既存の「メモ(任意・まとめ買い 等)」を維持)。
- [ ] 既存 `EmptyState` atom を買い物/履歴の空状態で使う(新規作成不要)。
- [ ] `:frontend:compileKotlinWasmJs` で文言参照が通ることを確認。

### Task 2: 買い物タブ忠実化(#3)

**Files:** `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/shopping/ui/ShoppingListScreen.kt`

- [ ] ヘッダ: faint サブ + 大タイトル(`MindstockType` の greeting/screenTitle、StockHome と同様)。
- [ ] 「在庫から探して追加」: 破線カードボタン(検索アイコン箱 + タイトル + サブ + plus)。`screens-c.jsx:69-80` 準拠。
- [ ] items ありのとき accent 進捗バナー(cart アイコン + 「あと N 点で買い物完了」+ done/total)。`screens-c.jsx:86-91`。
- [ ] セクションラベル「在庫が少ない」「自分で追加」。
- [ ] `ShopRow` を全面差し替え: surface カード(radius・lineSoft border・softShadow sm・done 時 alpha 0.5)。丸チェックトグル(28dp・選択時 accent+Check)、商品名(done で打消線)、メタ(auto=StatusDot+「目安 N{unit}」/ manual=「自分で追加」accentSoft バッジ+「在庫 N{unit}」)、manual の「外す」x ボタン(RoundBtn 近似)、`AppButton(variant=Soft,size=Sm,icon=Plus)` で「補充」。**全部 PrimaryButton だった現状を廃止**。
- [ ] 空状態は `EmptyState(icon=Check, ...)`。
- [ ] dev server で `screens-c.jsx` と横並び確認・スクショ。

### Task 3: 履歴タブ忠実化(#3)

**Files:** `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/activity/ui/ActivityScreen.kt`

- [ ] ヘッダを買い物と同じスタイルに。
- [ ] 日付グループごとに **1 枚の surface カード**(radius・lineSoft border・softShadow sm・overflow clip)。中の行は上 border で区切る。`screens-d.jsx:31-54` 準拠。
- [ ] 行: 動作アイコン箱(34dp・補充=accentSoft/Plus・消費=surface2/Minus)+ 商品名 + メタ(「補充 N{unit} · 名前 · 訂正済?」faint)+ HH:mm(faint・右)。**商品名を PrimaryButton で出す現状を廃止**、行全体を clickable に。
- [ ] 空状態は `EmptyState(icon=Clock, ...)`(既存文言)。
- [ ] dev server で `screens-d.jsx` と横並び確認・スクショ。

### Task 4: 下部ナビ グラスピル(#4)

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/shell/BottomNav.kt`
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/shell/AppShell.kt`

- [ ] `AppShell` を `NavigationSuiteScaffold` から `Box` ベースへ: 本文(下部に bar 分の padding)+ 下部中央に `BottomNav` を重ねる。`selected` を引数化(`selectedTab`/`onSelectTab`)し、`onAdd: () -> Unit` を追加。
- [ ] `BottomNav`: 浮遊角丸ピル(radius 22・左右余白 14・`surface` を ~82% alpha・lineSoft border・softShadow lg)。4 タブ(在庫/買い物/履歴/設定、選択時 accent・非選択 faint、アイコン+ラベル)+ 中央の盛り上がった accent + FAB(`onAdd`)。並びは 在庫・買い物・[+]・履歴・設定(`app.jsx` NAV 準拠)。
- [ ] 真の backdrop-blur は使わない(コメントで明記)。
- [ ] dev server で確認・スクショ。

### Task 5: 軽微配線(タブ遷移 hoist + stale + メモ)

**Files:** `App.kt`, `StockHomeScreen.kt`, `InventoryRoute.kt`, `SettingsViewModel.kt`(+ test), `MoveSheet.kt`

- [ ] App.kt: `var selectedTab by remember { mutableStateOf(Tab.Stock) }` を持ち、`AppShell(selectedTab, onSelectTab, onAdd = { catalogOverlay = AddProduct })` を配線。
- [ ] StockHome の SummaryStrip `onClick` を実配線: `onShop` コールバックを `StockHomeScreen`/`InventoryRoute` に追加 → App.kt が `onSelectTab(Tab.Shop)`。
- [ ] 世帯 pill `onClick`: `onOpenSettings` コールバック追加 → App.kt が `onSelectTab(Tab.Profile)`。
- [ ] `SettingsViewModel.renameDisplayName`: 成功時に `flow.applyDisplayName` に加えて `flow.refreshHouseholds()` を呼びメンバー一覧名を更新。VM テストで `refreshHouseholds` 呼び出しを検証。
- [ ] `MoveSheet`: mode に応じてメモ placeholder を出し分け(消費は `move_note_consume_placeholder`)。
- [ ] bell は不活性のまま(通知未実装・スコープ外)。

### Task 6: 仕上げ・検証

- [ ] `:frontend:spotlessApply` → `:frontend:compileKotlinWasmJs` `:frontend:compileKotlinJs` `:frontend:jsTest` `:frontend:spotlessCheck` 全緑。
- [ ] dev server 再起動 → 買い物/履歴/下部ナビ/各軽微配線を実機確認・スクショ。
- [ ] コミット(機能単位で分割)→ push → PR 作成。
