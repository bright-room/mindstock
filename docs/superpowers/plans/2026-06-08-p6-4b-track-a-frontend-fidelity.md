# P6-4b トラック A フロントエンド忠実化 実行プラン

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `frontend/` の各画面を、デザインモック `docs/ref/mindstock.zip` にプロパティ単位で忠実化する。各画面は mock 実描画 vs 実装実描画の同寸 side-by-side で確認してから done とする。

**Architecture:** 画面ごとに「①mock 実数値抽出→チェックリスト ②mock 実描画(正解) ③実装を同条件 render ④並べて ○/× ⑤差分修正 ⑥100%まで再描画 ⑦スクショ提示→commit」の **検証ループ(Verify Loop)** を回す。検証足場(mock サーバ + 実装プレビュー harness)を Phase 0 で固め、以後は同じループを画面に適用するだけ。

**Tech Stack:** Compose Multiplatform / Kotlin/Wasm(検証は軽い js: `:frontend:jsBrowserDevelopmentRun`)、mock = React/JSX(`python3 -m http.server` + CDN)、スクショ = Playwright(webapp-testing skill)。

---

## 0. 絶対原則(このプランの存在意義)

過去 fidelity が失敗したのは **JSX を読んで「近い」で done にし、モックにある機能を黙って落とした** から。本プランの構造はこれを物理的に防ぐためにある:

1. **render しない done を禁止**。各画面は「mock 実描画スクショ」と「実装実描画スクショ」の 2 枚を**必ず**生成し、ユーザに提示してからでないと done にしない。source 読みの自己申告は不可。
2. **「近い」を禁止**。合否は mock JSX の**実数値**(weight/size/lineHeight/letterSpacing/色hex/padding/gap/radius/影レベル/要素の有無/挙動)のプロパティ適合チェックリストで ○/×。合格 = 対象 100%(下記の除外フラグ付きを除く)。
3. **黙って落とさない**。backend 不足で再現不可な項目は checklist に `[backend]` フラグを立てて**明示**し、削除した事実を残す。意図的省略は `[省略・要確認]`。勝手に消さない。
4. **pixel 一致率は合否にしない**。mock=HTML、実装=Skia でフォント rasterize が違うため。pixel diff は「どこを見るか」のヒートマップ用途のみ。

---

## 1. 検証足場(Verify Loop の道具立て)

### 1a. mock 実描画(正解スクショ)

mock zip は文字化け HTML 名のため展開に注意([[full-replace-2026-06]] / handoff §1 の手順)。

```bash
# 展開(揮発 /tmp 前提・毎回再作成)
rm -rf /tmp/ms_ref && mkdir -p /tmp/ms_ref && cd /tmp/ms_ref
ZIP=<repo>/docs/ref/mindstock.zip
unzip -p "$ZIP" '*.html' > mock.html          # 文字化け名を回避
unzip -o -q "$ZIP" 'app/*' 'lib/*'            # JSX を個別展開
```

- デバイス切替 = `app/app.jsx` の `TWEAK_DEFAULTS.device`(`"mobile"|"desktop"`)を書き換える。
  両デバイスを撮るなら device を書き換えた mock を 2 つ用意するか、TweaksPanel を Playwright で操作する。
- 配信: `python3 -m http.server <port>`(JSX は XHR 取得で `file://` 不可・CDN React/Babel/Fonts を読むので**ネット必須**)。
- 画面/シートを開く: mock の `mountScreen` は `stock|shop|activity|profile`(`app.jsx:50-54`)。シート/詳細/オンボ/世帯は `H.*`/各 open ハンドラ。**mobile は iOS ベゼルが合成ポインタを intercept する**ので、開く操作は **desktop mock か Playwright の `force:true`/role セレクタ**で。
- Playwright(webapp-testing skill)で対象画面/シートをスクショ。

### 1b. 実装実描画(認証/backend 不要のプレビュー harness)

- 既存の committed サンプル: `feature/inventory/ui/StockHomePreview.kt` の `previewStocks()`(在庫系)。
- **一時 harness(uncommitted・最後に撤去)**:
  - `src/webMain/.../PreviewHarness.kt` … 各画面を sample state で mount する `@Composable PreviewHarness(name: String)`。
  - `Main.kt` を `window.location.search` の `?preview=<name>` で `App()` の代わりに `PreviewHarness(name)` を mount する分岐に一時改造。
  - 撤去は `git checkout src/webMain/.../Main.kt` / `rm PreviewHarness.kt`。**commit には含めない**。
- dev server: `./gradlew :frontend:jsBrowserDevelopmentRun`(wasmJs は重い/OOM、js は軽い・どちらも Skia なので見た目同等)。
- port 8080 居座り: `lsof -ti tcp:8080 | xargs kill -9` で解放してから再起動。
- Playwright で `http://localhost:8080/?preview=<name>` をスクショ。**mock と同じ幅**(mobile ≈ 390px 相当 / desktop は WideShell 幅)で撮る。

### 1c. 比較とチェックリスト

- 各画面につき `docs/superpowers/fidelity/<screen>.md` に **プロパティ適合チェックリスト**(要素 × プロパティ × mock値 × 実装値 × ○/×/フラグ)を保存。これが合否の一次資料。
- mock スクショ・実装スクショの 2 枚をユーザ提示(揮発パス `/tmp/ms-fidelity/<screen>/` 等に保存しパスを示す)。

---

## 2. ファイル構成(触る範囲)

- 土台: `designsystem/theme/MindstockType.kt`(実数値化・着手済)、`designsystem/theme/MindstockTokens.kt`(影レベル/色トークン)。
- 画面の修正: 各 `feature/<ctx>/ui/*.kt`(callsite で `.copy(...)` 上書き・padding/gap/radius/色トークン・欠落要素/挙動の補完)。
- atom 追加が要れば `designsystem/atom/*` に足してから feature で使う(feature は material3 直 import 禁止 / `LocalMindstockTokens` 経由)。
- 検証 harness(uncommitted): `src/webMain/.../PreviewHarness.kt` + `Main.kt` 一時分岐。
- チェックリスト(commit する): `docs/superpowers/fidelity/<screen>.md`。

---

## 3. 画面の処理順(優先度)

`docs/superpowers/specs/2026-06-08-p6-4b-fidelity-audit.md` の優先順位 + 「未 render 監査画面を優先」(メモリ)に従う:

1. **Phase 0** — 土台確定 + harness を StockHome で実証(lineHeight/trim が効くか1画面で確認)。
2. StockHome(在庫ホーム・既改修だが実数値未確認 / harness 実証も兼ねる)。
3. ProductDetail / MoveSheet / ProductSettings(改修済・実数値再点検)。
4. **ShoppingList / Activity**(未 render 監査)。
5. **Settings**(未 render 監査)。
6. **catalog: AddProduct / ProductMaster / Archived**(未 render 監査)。
7. **Onboarding / NeedHousehold / 世帯シート(Create/Join/Switcher)**(未 render 監査)。
8. シェル細部(WideShell サイドバー行 padding/active 配色 / BottomNav 質感)。

各 Phase = 1 画面(またはひとまとまり)= 1 commit、ユーザ提示チェックポイント。backend-blocked 項目は本プラン外(トラック B の別 PR)で、ここでは `[backend]` フラグ提示に留める。

---

## Task 0: 検証足場の実証 + 土台(MindstockType)を StockHome で確定

**目的:** Verify Loop が end-to-end で回ることを 1 画面で証明する。同時に、着手済の `MindstockType` 実数値化(特に CSS `line-height` を Compose で再現できているか = advisor 指摘の最重要リスク)を実描画で検証する。**ここが通らなければ以降は全部砂上の楼閣。**

**Files:**
- Modify(着手済): `frontend/src/commonMain/.../designsystem/theme/MindstockType.kt`
- Create(uncommitted): `frontend/src/webMain/.../PreviewHarness.kt`
- Modify(uncommitted): `frontend/src/webMain/.../Main.kt`
- Create(commit): `docs/superpowers/fidelity/stock-home.md`

- [ ] **Step 1: mock を展開して StockHome(mobile)を実描画**

§1a の手順で展開 → `python3 -m http.server 8090`(repo 外 /tmp/ms_ref で) → webapp-testing skill で `http://localhost:8090/mock.html`(device=mobile, screen=stock)を `/tmp/ms-fidelity/stock-home/mock-mobile.png` に保存。
Expected: SummaryStrip + 検索 + セグメント + ProductCard 群が描画された正解スクショが取れる。

- [ ] **Step 2: 実装プレビュー harness を組む(uncommitted)**

`PreviewHarness.kt` に StockHome を `previewStocks()` の sample state で mount する分岐を作り、`Main.kt` を `?preview=stock-home` で `PreviewHarness("stock-home")` を mount するよう一時改造。`App.kt` の StockHome 経路(`InventoryRoute`/`StockHomeScreen`)がどの引数を要るか read して埋める。

- [ ] **Step 3: 実装を同幅で実描画**

`lsof -ti tcp:8080 | xargs kill -9` → `./gradlew :frontend:jsBrowserDevelopmentRun` → webapp-testing skill で `http://localhost:8080/?preview=stock-home` を mock と同じ mobile 幅で `/tmp/ms-fidelity/stock-home/impl-mobile.png` に保存。
Expected: 実装の StockHome が描画される。

- [ ] **Step 4: チェックリスト作成 + 並べて照合**

`docs/superpowers/fidelity/stock-home.md` に `screens-a.jsx` の実数値で要素×プロパティ表を作る(例: 挨拶 `500 13px/1` faint / タイトル「在庫」`800 25px/1.1` ls-0.02em / SummaryStrip バナー padding18 radius lg(22) 影 md・アイコン箱44 radius13 gap14 / 見出し `700 16px/1.2`・副文 `500 12.5px/1.3` / 検索 height50 radius14 / セグメント `600 13px/1` / ProductCard padding18 radius lg 影 sm border lineSoft gap14・名前 `600 15.5px/1.35`・数量 `700 30px/0.9` tnum・単位 `500 11.5px/1` / 予測「あと約X日」= `[backend]` 非表示)。mock/impl スクショを並べて各項目 ○/×/フラグ。

- [ ] **Step 5: lineHeight/trim の効きを判定(最重要)**

タイトル「在庫」・数量・商品名の縦リズムが mock と一致するか確認。`LineHeightStyle(trim=Both, Center)` で締まっていなければ、trim 値・`lineHeight` 倍率・`includeFontPadding` 相当の扱いを調整して再描画。**ここで「Compose で CSS line-height をどう出すか」の正解を確定**し、KDoc に残す。

- [ ] **Step 6: 差分を修正(callsite + token)**

× の項目を潰す。typography は `MindstockType` baseline + callsite `.copy(fontSize=…)` 等。余白/角丸/影/色は token 経由。要素欠落/挙動差も補完。修正のたび Step 3 を回して再描画。

- [ ] **Step 7: 100% 確認 → ユーザ提示**

checklist の ○ が(フラグ除外で)全項目になるまで Step 6↔3 を反復。最終 mock/impl スクショ 2 枚のパスをユーザに提示。

- [ ] **Step 8: gate + commit(harness は除外)**

```bash
./gradlew :frontend:compileKotlinJs :frontend:jsTest spotlessCheck
git checkout frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/Main.kt
rm -f frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/PreviewHarness.kt
git add frontend/src/commonMain/.../designsystem docs/superpowers/fidelity/stock-home.md  # 対象パス明示・lock/未追跡 doc を巻き込まない
git commit -m "fix(frontend): MindstockType を core.jsx 実数値化・StockHome を実描画で忠実化"
```
Expected: 全 gate 緑。`.claude/scheduled_tasks.lock` 等を巻き込んでいない。

---

## Task 1..N: 各画面の Verify Loop(§3 の順)

各画面は **Task 0 の Step 1〜8 と同一手順**を適用する(screen 名・mock コンポーネント・sample state だけ差し替え)。プラン肥大を避けるため実数値の事前抽出は各タスク内で行う(抽出自体が忠実化作業の一部)。各タスクで最低限固定する項目:

### Task 1: ProductDetail(mock `screens-c.jsx:ProductDetail` / sample = previewStocks の 1 件 + history)
- 重点: 数量 `700 46px/0.9` の行高・単位位置・StatusDot ラベル色・最低在庫の右寄せ・履歴コネクタ線/ノード配色/アバター色(mock USERS 別色 → id 由来で色分け = frontend で直す手抜き項目)・設定アイコン sliders(mock) vs 歯車。
- `[backend]`: 消費/補充の日時設定(occurredAt)。
- done: mock/impl スクショ 2 枚 + checklist 100%。

### Task 2: MoveSheet(mock `screens-b.jsx:MoveSheet` / sample = 1 商品 + Stepper)
- 重点: サマリ箱の影/角丸/gap・Stepper(RoundBtn 58 / 数字 `700 52px/1` tnum)・submit ボタン高さ lg=56・字間。
- `[backend]`: 日時ピッカー(occurredAt)。

### Task 3: ProductSettings(mock `screens-master.jsx:MasterItemSheet`)
- 重点: タイトル「商品マスタの編集」・単位チップ選択色/枠/間隔・mini ステッパ(mock 40/radius11 角丸スクエア vs 実装 円形)。
- `[省略・要確認]`: 画像欄 ImageField(`[backend]` 画像基盤)。

### Task 4: ShoppingList(mock `screens-c.jsx:ShoppingList` / sample = wanted 混在 stock)
- 重点: 進捗バナー「あと X 点で買い物完了」+0/N・カード行・丸チェック・StatusDot・目安・補充ボタン・「在庫から探して追加」。PR #118 で clay 化済だが実数値未確認。
- `[backend]`: 予測「目安」。

### Task 5: Activity(mock `screens-d.jsx` / sample = 日付跨ぎ movement)
- 重点: 日付カード+区切り行・タイムライン・アバター別色・相対/絶対時刻・**訂正の畳み込み**(ProductDetail と同様に別行で積まないか)。

### Task 6: Settings(mock `app.jsx:Profile` / sample = 世帯+メンバー)
- 重点: アカウント/世帯カード・メンバー行・招待・退出・環境設定トグル(present だが no-op を明示)・その他・ログアウト。

### Task 7: catalog AddProduct / ProductMaster / Archived(mock `screens-b/master.jsx`)
- 重点: 検索/JAN/採用フォーム/カスタム・一覧/アーカイブのヘッダ/行/空状態。
- `[省略]`: カメラ/バーコードスキャナ。`[backend]`: 画像。

### Task 8: Onboarding / NeedHousehold / 世帯シート(mock `screens-onboard/household/join/invite.jsx`)
- 重点: 進捗バー・ステップ番号・入力欄・チップ・確認カード / Create・Join・Switcher の各シート。
- 実機通し(join・オンボ)は 2nd Zitadel ID 要 = render 忠実化のみ本タスク・実機は別途。

### Task 9: シェル細部(WideShell / BottomNav)
- 重点: サイドバー行 padding/active 配色/フッタ・BottomNav 浮遊ピル質感(blur 近似は限界明記)。

---

## 4. backend-blocked の扱い(トラック B 申し送り)

本プラン(トラック A)では再現せず checklist に `[backend]` で明示するのみ。実装は別 PR(全て実装確定): occurredAt / 画像 / 消費予測 / 通知。詳細はメモリ [[p6-4b-fidelity-program]]。

---

## 5. Self-Review(プラン点検)

- **spec 網羅**: audit の §1-10 各画面 → Task 0-9 に対応。土台(§0)= Task 0。○。
- **placeholder**: 検証手順・コマンド・チェックリスト保存先・撤去手順は具体。実数値の事前全抽出はしない(抽出が作業本体・全部書くと「プランで全部やる」ことになる)旨を明記済 = 意図的。○。
- **anti-fooling**: 「render 2 枚提示まで done 禁止」「100% ○ まで反復」を各タスクの構造に埋め込み済。○。
- **依存/型整合**: harness は uncommitted・撤去手順あり。commit パス明示で lock 巻き込み回避。○。
