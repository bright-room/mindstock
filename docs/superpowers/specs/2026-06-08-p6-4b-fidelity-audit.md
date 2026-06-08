# P6-4b 画面忠実度 差分監査

モック(`docs/ref/mindstock.zip`)に対する frontend 実装の**差分を網羅的にリスト化**する文書。
これまで「近い」で目視判断して取りこぼした反省から、**私(実装者)が全差分を出す**ためのもの。

## 方法（このプロジェクトの忠実化ルール）

1. **差分は実数値で**: モック JSX の `font: WEIGHT SIZEpx/LINEHEIGHT`・色・padding・gap・radius・shadow をリテラル仕様として扱う。「近い値」で済ませない。
2. **挙動も対象**: 見た目だけでなく、モックの**振る舞い**（何が表示/設定/操作できるか）を再現する。バックエンドで無理な所は**勝手に落とさず明示**して判断を仰ぐ。
3. **fix は render-verify**: 各画面の修正後、モック実描画 vs 実装実描画を**同寸 side-by-side** で確認してから「忠実」とする。source 読みの自己申告で done にしない。
4. 各差分に **再現可否** を付す: `[now]` 表示/フロントだけで再現可 / `[backend]` バックエンド改修要(別 PR) / `[要render]` 実描画で確認してから確定。

## 0. 土台（atom/トークン）— 全画面に波及する系統的ズレ ★最優先

- **`MindstockType` に lineHeight が無い** → Compose 既定行高(≈1.4-1.5)で、モックのタイト行高(`/1.0`〜`/1.35`)とズレ、全テキストの縦リズムが緩い。`[now]`
- **letterSpacing 未適用** → モックの大見出しは `letter-spacing: -0.02em`（`mindstock`/`ようこそ`/数量）。タイトルが間延び。`[now]`
- **font-weight の一部不一致** → 例: 商品名 700(モック) vs `summaryTitle`=Bold だが画面側で `.copy(fontSize)` だけして weight を上書きしている箇所あり。`[要render]`
- **影**: `softShadow` は CSS の多層 box-shadow（sm/md/lg/pop の正確な rgba）を近似。Compose の単層 shadow で完全一致は不可だが、レベル対応とサイズ感を core.jsx 値へ寄せる。`[now/一部不可]`
- **`tnum`(等幅数字)**: `bigQty` は設定済。他の数値表示(在庫数・人数等)に未適用の所。`[要render]`
- → **対応**: `MindstockType` 各スタイルに lineHeight/letterSpacing/weight を core.jsx の実数値で付与。これだけで全画面の「なんか緩い/違う」がかなり消える想定。

## 1. 在庫ホーム StockHome（`feature/inventory/ui/StockHomeScreen.kt`）

- [now] SummaryStrip バナー: アイコン箱・文言レイアウト・chevron・影・角丸をモック `screens-a.jsx:SummaryStrip` の実数値と突合（カード内アイコン 44/13・gap 14 等）。`[要render]`
- [now] 予測バナー「◯◯ はあと約X日で切れる予測です」= モックにあるが**消費ペースが domain に無く未実装**（既知の意図的非対応）。→ 出さない方針を明記（モックと差あり・data 由来）。`[backend/見送り]`
- [now] ProductCard: サムネ48/名前/StatusDot+ラベル/「· あと約X日」(予測=出さない)/数量 大/StockBar/補充・消費。余白・影・行間を実数値で突合。`[要render]`
- [now] グリッド CompactCard のモック突合。`[要render]`
- [now] 検索欄・セグメント・件数表記の字間/色。`[要render]`

## 2. 商品詳細 ProductDetail（改修済・再点検）

- [done/now] 訂正の畳み込み・訂正理由・相対時刻 → 実装済(render 確認済)。
- [now] 数量カードの数値 46px の行高/字間、単位の位置、StatusDot ラベル色、最低在庫の右寄せ位置を実数値突合。`[要render]`
- [now] 履歴コネクタ線の位置・ノード配色・アバター色（モックは USERS 別色 / 実装は accent 固定）。モックは利用者ごとに色違い → 実装は単色。`[now・色割当て要]`
- [backend] **消費/補充の日時「設定」**（DatePick）= ~~occurredAt をサーバ確定のため未対応~~ → **P6-4b トラックB #1 で実装済**（補充/消費に DatePick・クライアント指定 occurredAt）。`2026-06-08-occurred-at-backdate-design.md`。
- [now] 設定アイコン: モックは `sliders`（横スライダー）、実装は `Settings`(歯車)。アイコン差。`[now]`

## 3. 補充/消費シート MoveSheet（改修済・再点検）

- [done/now] 商品サマリ箱・増減プレビュー → 実装済(render 確認済)。
- [backend] 日時ピッカー（モックにある）→ occurredAt 別 PR。
- [now] サマリ箱の影/角丸/gap、submit ボタン高さ(lg=56)・字間を実数値突合。`[要render]`

## 4. 商品マスタ編集 ProductSettingsSheet（改修済・再点検）

- [done/now] タイトル・最低在庫の上下ボーダー → 実装済。
- [見送り] 画像欄(モックの ImageField) = アップロード基盤無しで省略（意図的・要ユーザ確認なら別途）。
- [now] 単位チップの選択色/枠/間隔、mini ステッパの形（モックは 40/radius11 角丸スクエア、実装は円形 Stepper）。`[now・差あり]`

## 5. 買い物リスト ShoppingList（`feature/shopping/ui/ShoppingListScreen.kt`）★未 render 監査

- [要render] モック `screens-c.jsx:ShoppingList` と突合（進捗バナー「あと X 点で買い物完了」+ 0/N・カード行・丸チェック・StatusDot・目安・補充ボタン・「在庫から探して追加」）。
- [now] PR #118 で clay 化済だが実数値忠実度は未確認。`[要render]`

## 6. 活動/履歴 ActivityScreen（`feature/activity/ui/ActivityScreen.kt`）★未 render 監査

- [要render] モック `screens-d.jsx` と突合（日付カード+区切り行・タイムライン・アバター別色・相対/絶対時刻）。
- [now] 訂正の扱い（活動でも訂正が別行で出ていないか＝ProductDetail と同じ畳み込みが要るか）。`[要確認]`

## 7. 設定タブ SettingsScreen（`app/settings/SettingsScreen.kt`）★未 render 監査

- [要render] モック `app.jsx:Profile` と突合（アカウントカード・世帯カード・メンバー行・招待・退出・環境設定トグル・その他・ログアウト）。
- [now] 環境設定トグル（在庫減少のお知らせ/オフライン）はモックにあるが将来機能 → present だが no-op か明示。

## 8. 商品追加 AddProduct / アーカイブ / マスタ一覧（catalog）★未 render 監査

- [要render] AddProductScreen をモック `screens-b/master` と突合（検索/JAN/採用フォーム/カスタム）。
- [見送り] カメラ/バーコードスキャナ（モックにある）= 未実装（意図的）。
- [要render] ProductMasterScreen / ArchivedScreen のヘッダ・行・空状態。

## 9. オンボーディング / ウェルカム / 世帯（onboarding・welcome・household）★一部 render 済

- [done] WelcomeScreen = render 確認済(モック忠実)。
- [要render] OnboardingScreen 4 step（welcome/name/household/confirm）をモック `screens-onboard.jsx` と突合（進捗バー・ステップ番号・入力欄・チップ・確認カード）。
- [要render] NeedHousehold / CreateHouseholdSheet / JoinCodeSheet / HouseholdSwitcher をモック `screens-household/join/invite.jsx` と突合。

## 10. シェル（既改修・再点検）

- [done] WideShell サイドバー / BottomNav 浮遊ピル / 在庫ヘッダの desktop ピル・ベル非表示。render 確認済。
- [要render] サイドバーの各行 padding/active 配色/フッタ、ボトムナビの blur 近似の質感。

## バックエンド要（全 4 件 実装決定・各 spec→plan→別 PR、2026-06-08 ユーザ確定）

全 RPC 面と突合した結果、mock 機能のうち backend 不足でフロントに反映できないのは以下 4 件のみ。**全て「実装する」で確定**（後出し禁止）。

1. **occurredAt の設定（バックデート）**: `replenish/consume/correct` のサーバ時刻固定を解除。RPC→Service→domain(`Stock`)→datasource 横断 + frontend(MoveSheet 日時ピッカー・履歴の実日時)。過去の「occurredAt サーバ確定」決定を覆す（spec/ルール更新）。
2. **商品画像（撮影/選択・表示）**: アップロード→ImageRef 生成 と ImageRef→配信 の基盤を作る（`changeImage` RPC は既存）。frontend は Thumb 画像表示化 + 設定 UI。
3. **消費予測「あと約X日」/予測バナー**: domain に消費履歴からのレート推定を実装。在庫ホームのトレンドバナー・shopping もこれ依存。
4. **通知（ベル/お知らせ/Web Push）**: 通知スキーマ+生成+Web Push。ベル・NotifSheet を実配線。

詳細・working agreement・忠実度指標はメモリ `p6-4b-fidelity-program` 参照。

## backend gap でない（frontend で私が直す手抜き）

- アバターの利用者別カラー（mock=USERS 別色 / 実装=accent 単色）→ id から導出して色分け。`[now]`
- 設定アイコン sliders（mock）vs 歯車（実装）等のアイコン差。`[now]`

## 優先順位（fix 順）

1. **土台（MindstockType lineHeight/letterSpacing/weight + 影レベル）** — 全画面に効く。
2. ProductDetail / MoveSheet / ProductSettings の実数値再点検（render A/B）。
3. ShoppingList / Activity（未 render 監査 → render して差分確定 → 修正）。
4. Settings / catalog / onboarding / household（同上）。
5. occurredAt 改修（別 PR）。

各ステップで**モック vs 実装の同寸 side-by-side を出してから done**にする。
