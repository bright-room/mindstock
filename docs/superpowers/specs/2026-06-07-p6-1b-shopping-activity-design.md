# P6-1b: 買い物リスト / 活動タブ・setWanted 設計

フルリプレイス最終フェーズ **P6（`:frontend`）** のサブプロジェクト P6-1 の後段。
P6-1a（在庫スパイン：live 配線・StockHome・ProductDetail・補充/消費/訂正・トースト・再認証）の上に、
**買い物リストタブ・活動タブ・ProductDetail の買い物リスト入/外（`setWanted`）** を積む。

- 親 spec: [2026-06-06-p6-1-inventory-frontend-design.md](2026-06-06-p6-1-inventory-frontend-design.md)（P6-1b のスコープと確定済み設計判断を記述）
- 起点モック: `docs/ref/mindstock.zip`（`app/screens-c.jsx`=ShoppingList/AddToListSheet/ProductDetail、`screens-d.jsx`=ActivityTab、`app/app.jsx`=タブ/オーバーレイ構造）
- backend は P0–P5c + WS-RPC 再設計で完成済み。全 RPC は単一 `/api/rpc`、`RpcClientProvider.service<T>()` で取得。

## スコープ（ユーザ承認済み）

| 含む | 含まない（送り先） |
|---|---|
| 買い物リストタブ（`shoppingList`） | 商品追加 / 設定 / アーカイブ（P6-2） |
| 「在庫から探して追加」シート（`AddToListSheet`・`setWanted(true)`） | ログイン/オンボーディング・世帯切替/作成・招待・設定（P6-3） |
| 活動タブ（`activity`） | 消費予測「あと約 N 日」（モデル未導入・親 spec の決定どおり除外） |
| ProductDetail の wanted トグル（`setWanted`） | 通知（bell / NotifSheet・将来機能） |
| ProductDetail を app 層共有オーバーレイへ昇格（3 タブから `productId` で開く） | Profile タブ本実装（P6-3 placeholder 据置） |

## 利用する RPC（すべて `RpcResult<T, RpcError>`）

| サービス | メソッド | 返り | 用途 |
|---|---|---|---|
| `ProductRpcService` | `shoppingList(householdId)` | `ShoppingList` | 買い物リスト + 詳細の Stock/wanted 解決 |
| `StockRpcService` | `activity(householdId)` | `ActivityFeed` | 世帯の活動履歴 |
| `ProductRegisterRpcService` | `setWanted(productId, wanted)` | `Unit` | 手動希望の入/外 |
| `StockRpcService` | `history(productId)` | `StockMovements` | 詳細の履歴（既存） |
| `StockRegisterRpcService` | `replenish/consume/correct` | `Unit` | 詳細・行クイック操作（既存） |

### ドメイン/RPC 型の要点（実装確認済み）

- `ShoppingList(list: List<ShoppingEntry>)`。`ShoppingEntry(stock: Stock, manuallyWanted: Boolean)`、`need(): ShoppingNeed(在庫不足|手動希望|不要)`、`onList()`。`autoItems()`/`manualItems()` で区分。
- **`shoppingList(householdId)` は世帯の全 stock に対し `ShoppingEntry` を返す**（on-list のみではない）。よって各 entry は完全な `Stock`（数量/status/最低在庫）＋ `manuallyWanted` を持ち、これ 1 本で買い物リスト・詳細ヘッダ・wanted トグルの全データが揃う。
- `ActivityFeed(list: List<ActivityEntry>)`。`ActivityEntry(product: Product, movement: StockMovement)`。`StockMovement` は `occurredAt: OccurredAt(LocalDateTime)` / `actor.profile.displayName()` / sealed（Replenishment/Consumption/Correction）。
- `setWanted` は `ProductId`（global UUID・世帯一意）のみで householdId 不要。

---

## 1. 共有リフレッシュ機構（横断・新規）

ProductDetail を **app 層の共有オーバーレイ**へ昇格すると、オーバーレイは全タブの上に乗る。そこでの mutation（補充/消費/訂正/`setWanted`）を、裏で生きているタブ（Stock/Shop/Activity）へ波及させる必要がある（モックは global store で自動反映、本実装はタブごとに独立 VM のため明示同期が要る）。

- `core/ui/InventoryRefreshController`：`ReauthController` と同型の `MutableSharedFlow<Unit>`（`extraBufferCapacity = 1`、`tryEmit`）。
- **発火**：在庫に影響する mutation（replenish / consume / correct / setWanted）の成功時に各 VM が `request()`。
- **購読**：在庫由来の一覧 VM（`InventoryViewModel`=Stock、`ShoppingListViewModel`=Shop、`ActivityViewModel`=Activity）が signal を collect して自分の `load()` を再実行。
- App.kt に 1 つ生成し、各 VM へ DI。テスト容易（controller を注入して emit/collect を検証）。

---

## 2. Shop タブ（`feature/shopping/` 新設）

### 2.1 ViewModel

`ShoppingListViewModel(householdId, loadShoppingList, setWantedFlag, replenishStock, refresh, toast, reauth)`。

- `load()`：`shoppingList(householdId)` → `ShoppingListUiState.Content(list)`。失敗は `Error`（`Unauthorized`→reauth、他→トースト）。
- `setWanted(productId, wanted)`：`setWanted` 成功→`load()` + `refresh.request()` + トースト。失敗は分岐。
- `replenish(productId, qty, note)`：行のクイック補充。成功→`load()` + `refresh.request()` + トースト。**買い物リストからの補充は `OccurredAt.now()` 固定**（クイック補充。日付ピッカー非表示。バックデートは商品詳細の MoveSheet で行う）。
- refresh signal の購読は **UI 層の `LoadWithRefresh`（`App.kt`）で一元化する**（VM は `load()` を提供するのみで、自前で refresh を collect しない）。

区分は domain のメソッドを使う：`autoItems()`（在庫不足）/`manualItems()`（手動希望）。「在庫から探して追加」候補は `entry.onList() == false` のもの。

### 2.2 画面（`ui/ShoppingListScreen.kt` + `ui/AddToListSheet.kt`）

モック `screens-c.jsx` 準拠：

- ヘッダ（小見出し「在庫が少ない商品と、自分で追加した商品」/「買い物リスト」）。
- 「在庫から探して追加」ボタン（破線・検索アイコン）→ `AddToListSheet`。
- 進捗バナー（`Sheet`/`Toast` と同トーンの accent カード）「あと N 点で買い物完了」＋`done/total`。`done` は **ローカル一時状態（非永続）**（モックどおり・チェックは買い物中のメモ）。
- 自動セクション（在庫が少ない）→ `manualItems` があるときのみ見出し。手動セクション（自分で追加）→「自分で追加」バッジ＋✕（`setWanted(false)`）。
- 各行：チェックトグル（ローカル）/ 商品名タップ→`onOpenProduct(entry.stock.product.id, seed = entry.stock)` / `補充`クイックボタン。
- 空状態：`EmptyState`「買うものはありません」。
- `AddToListSheet`（`Sheet` atom 上）：検索 `TextInput` + 候補（`onList()==false`）リスト、選択で `setWanted(true)`。追加済みは即リストから消える（`load()` 再取得）。

新規 atom は最小限に：進捗バナー/チェック丸/破線追加ボタンは designsystem の既存 atom（`Sheet`/`TextInput`/`StatusDot`/`PrimaryButton`/`Thumb`）＋トークンで構成。共通化が要るものだけ atom 化（実装で判断、原則 feature 内に閉じる）。

---

## 3. Activity タブ（`feature/activity/` 新設）

### 3.1 ViewModel

`ActivityViewModel(householdId, loadActivity, refresh, toast, reauth)`。

- `load()`：`activity(householdId)` → `ActivityUiState.Content(feed)`。失敗は `Error`/reauth。
- refresh signal の購読は **UI 層の `LoadWithRefresh`（`App.kt`）で一元化する**（VM は `load()` を提供するのみ。補充/消費/訂正で feed が伸びる）。
- mutation は持たない（閲覧のみ）。行タップは詳細へ。

### 3.2 日付グルーピング（純関数・TDD）

`feature/activity/ActivityGrouping.kt`：`ActivityFeed` → 日ラベル順のグループ。

- `relDayLabel(occurredAt: LocalDateTime, today: LocalDate): UiText`：今日 / 昨日 / N日前（< 7 日）/ 日付（`fmtDate` 相当）。`today` を注入し純関数化（テスト可能）。
- 並びは `occurredAt` 降順。`hm(occurredAt)` で `HH:mm`。
- `today` は画面側で `LocalDate.now(TimeZone.JST)`（`:shared` の datetime ext）を与える。
- 文言（今日/昨日/N日前）は string resources を返す `UiText`（非 Composable 層の文言は `UiText`、親 spec 7 節の方針）。

### 3.3 画面（`ui/ActivityScreen.kt`）

モック `screens-d.jsx` 準拠：日ラベル見出し＋カード内に行（補充=plus/accent・消費=minus、`{補充|消費} {qty}{unit} · {actor} {訂正済?}`、右に `HH:mm`）。行タップ→`onOpenProduct(entry.product.id)`（seed 無し）。空状態「まだ記録がありません」。

「訂正済」表示：feed 全体から `Correction.target` 集合を作り、その対象 movement に付与（ProductDetail の既存ロジックと同型）。

---

## 4. ProductDetail オーバーレイ（昇格 + 拡張）

### 4.1 データ源の統一（shoppingList 軸）

`ProductDetailViewModel` を **`householdId` + `productId` 軸**へ再設計（P6-1a の「Stock を渡し history のみ」からのリファクタ）：

- `load()`：`shoppingList(householdId)` から `productId` 一致の `ShoppingEntry` を解決 → `Stock`（ヘッダ/バー/status）＋ `manuallyWanted`（トグル）。並行/直列で `history(productId)`。`ProductDetailUiState.Content(stock, wanted, movements)`。
- **seed Stock**（任意）：stock/shop タブは開く時点で `Stock` を持つので seed を渡し、ロード中も即ヘッダ描画（フラッシュ回避）。activity は seed 無し（productId のみ）→ ロード表示。
- entry 不在（例：アーカイブ済を activity から開く等の端ケース）→ seed があれば seed、無ければ `Error`。P6-1 はアーカイブ非対象（P6-2）なので最小対応。

### 4.2 mutation（全てこの VM 経由・refresh 発火）

- `replenish` / `consume` / `correct` / `setWanted`：成功→`load()` 再取得 ＋ `refresh.request()` ＋ トースト。失敗は `Unauthorized`→reauth、`Conflict`/`BadRequest`/`Internal`→トースト/フィールド（親 spec 3.2）。
- `correct` の target は履歴行の `MovementId`（`movement.identity` の `Persisted`）。

### 4.3 トグル UI（モック準拠）

`status != 十分` → 静的「在庫が少ないため、買い物リストに表示中です」（トグル無し＝自動 on）。
`十分 && wanted` → 「買い物リストから外す」→ `setWanted(false)`。
`十分 && !wanted` → 「買い物リストに入れる」→ `setWanted(true)`。

`MoveSheet`/`CorrectionSheet`（既存）はオーバーレイ内に内包（現 `InventoryRoute` から移設）。

---

## 5. AppShell / App.kt 配線

- `AppShell` に `shopContent` / `activityContent` スロットを追加（`stockContent` と同様の `@Composable () -> Unit`）。Profile は P6-3 placeholder 据置。`when(selected)` で各スロットを描画。
- **オーバーレイは App が持つ**：`var opened: DetailTarget? by remember`（`DetailTarget(productId, seed: Stock?)`）。`opened != null` のとき `AppShell` の上層（Toast と同じ Box）に `ProductDetailOverlay` を描画。`onBack` で `opened = null`。
- 各タブ route に `onOpenProduct: (ProductId, Stock?) -> Unit` を配線（App の `opened` を更新）。
- `InventoryRefreshController` を App で生成、4 VM（Stock/Shop/Activity/Detail）へ DI。
- `InventoryRepository` に `shoppingList(householdId)` / `activity(householdId)` / `setWanted(productId, wanted)` を追加（`ProductRpcService` / `StockRpcService`（activity）/ `ProductRegisterRpcService` の「開く関数」を DI）。

`InventoryRoute`（現 stock タブの束ね）は、詳細・MoveSheet・CorrectionSheet をオーバーレイへ移すため再構成：StockHome は `onOpenProduct` を上げるだけ、クイック補充/消費は引き続き `InventoryViewModel` 経由（成功で refresh 発火）。

---

## 6. i18n / テスト / 検証

### i18n
UI 文言は `commonMain/composeResources/values/strings.xml`（ja）へ追加（買い物リスト見出し・「自分で追加」・「あと N 点で買い物完了」・「在庫から探して追加」・空状態・wanted トグル文言・活動見出し・今日/昨日/N日前・補充/消費トースト追加分）。コード直書き禁止。非 Composable 層は `UiText`。

### テスト（commonTest・`kotlin.test.@Test` + Kotest assertions、FunSpec 不可）
- `ShoppingListViewModel`：load / 区分（auto・manual）/ `setWanted`→reload＋refresh発火 / クイック補充→reload＋refresh / エラー→トースト・reauth / refresh 受信→reload。
- `ActivityViewModel`：load / エラー / refresh 受信→reload。
- `ActivityGrouping`（純関数）：今日/昨日/N日前/日付の境界、降順、HH:mm 整形。
- `ProductDetailViewModel`：shoppingList 由来の Stock/wanted 解決 / seed フォールバック / setWanted / replenish/consume/correct → reload＋refresh / エラー分岐。
- `InventoryRefreshController`：emit→collect。
- `InventoryRepository`：追加 3 メソッドの委譲（`toOutcome` 変換）。
- UI 描画網羅は追わない。

### 検証
`./gradlew :frontend:compileKotlinWasmJs` ＋ `:frontend:jsTest`（`--tests` フィルタ非対応＝全体）。フルビルドはローカル OOM しうるので避ける。live 検証は backend（Zitadel/Postgres）起動が要るため最終判断はユーザが実機で行う（frontend visual fidelity は実装が揃ってから実機確認＝ユーザ方針）。

実装は従来どおり Subagent-Driven ではなく、仕様確定済みの機械的 frontend タスクとして **インライン実装 + まとめビルド**（過去フィードバック反映）。

---

## 7. 申し送り・非スコープ

- 世帯切替・Profile 本実装・商品追加/設定/アーカイブは P6-2/P6-3。
- 通知（bell）は将来機能（本実装しない）。
- 消費予測「あと約 N 日」はモデル未導入のため出さない（親 spec の決定）。
- 下部ナビ/シート/アニメの最終トーン調整は、P6 実装が揃ってからユーザが実機で判断（visual fidelity 方針）。

## 関連
- spec（親）: [2026-06-06-p6-1-inventory-frontend-design.md](2026-06-06-p6-1-inventory-frontend-design.md)
- spec（土台）: [2026-06-05-p6-0-frontend-foundation-design.md](2026-06-05-p6-0-frontend-foundation-design.md)
- mock: `docs/ref/mindstock.zip`（`app/screens-c.jsx` / `screens-d.jsx` / `app/app.jsx`）
- rule（適用）: `frontend-architecture.md` / `frontend-rpc-and-error.md` / `frontend-designsystem.md` / `frontend-i18n-and-font.md` / `frontend-compose-conventions.md` / `frontend-kmp-structure.md` / `error-handling.md` / `testing.md`
