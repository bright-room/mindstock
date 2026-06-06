# P6-1: 在庫まわり frontend（StockHome live 配線）設計

フルリプレイス最終フェーズ **P6（`:frontend` / Compose Multiplatform Wasm）** の 2 番目のサブプロジェクト。
P6-0 で確立した土台（認証 / RPC / セッション / テーマ / shell / 規約 + 参照画面 StockHome の単体テスト疎通）の上に、**在庫操作の実画面を live に配線**する。

- 起点モック: `docs/ref/mindstock.zip`（`app/screens-a.jsx`=StockHome/ProductCard/CompactCard/SummaryStrip、`screens-b.jsx`=MoveSheet、`screens-c.jsx`=ProductDetail/HistoryRow/CorrectionSheet、`screens-d.jsx`=ActivityTab、`app/core.jsx`=トークン/atoms）。iOS デバイス枠・tweaks パネル・トーン切替・通知（bell/NotifSheet）はプロトタイプ用の足場であり**製品には含めない**。
- backend は P0–P5c + WS-RPC 再設計（PR #110）で完成済み。全 RPC は単一エンドポイント `/api/rpc` に相乗りし、`RpcClientProvider.connect()` の単一接続から `service<T>()` で取り出す。
- P6-0 の holistic レビューで「`App()` が `StockHomeScreen` を **live レンダリングする配線**（実 `householdId` の取得 = `household.list()` / 世帯選択を要する）は P6-1 の最初のタスク」と申し送られた。本 spec のセクション 2 がその本体。

## スコープと進め方

P6-1 は **1 spec・2 フェーズ**で進める（ユーザ承認済み）。spec はフェーズ両方を記述し、実装は **P6-1a → P6-1b の順**で段階実施する。

| フェーズ | 範囲 |
|---|---|
| **P6-1a（スパイン優先）** | live 配線スパイン（boot→`household.list()`→`AppSession`→Repository DI→ViewModel→StockHome）/ 在庫ホーム（grid/list/検索）/ 商品詳細（数量・在庫バー・商品履歴）/ 補充・消費シート / 訂正シート / 書込→再取得 / トースト機構 / `Unauthorized`→再認証導線 / 新規 atom（Sheet/Stepper/Thumb/RoundBtn/Toast）/ `StockLevelBar.trackColor` |
| **P6-1b（周辺タブ）** | 買い物リストタブ（`shoppingList`）/ 活動タブ（`activity`）/ 商品詳細の買い物リスト入/外（`setWanted`） |

### 確定済みの設計判断（ユーザ承認済み）

| 論点 | 決定 | 理由 |
|---|---|---|
| 内部分割 | スパイン優先 2 フェーズ（1 spec 内） | 在庫操作の背骨を先に通し、周辺タブを後段で積む |
| 予測「あと約X日」 | **出さない**（UI から予測行/バッジを省く） | ドメインに消費ペース（daily rate）モデルが無い。導入は将来モデル追加時に別途 |
| 日時ピッカー（バックデート） | **外す**。補充/消費=数量+メモ、訂正=数量+理由 | `replenish`/`consume`/`correct` は `occurredAt` を受け取らずサーバ時刻で確定する（P3 決定） |
| `setWanted` | P6-1b に寄せる。P6-1a の ProductDetail に wanted トグルは出さない | 買い物リスト概念は P6-1b でまとめて active 化する方が一貫 |
| 世帯ゼロ | P6-1 では扱わない（オンボーディング/P6-3 送り） | 世帯作成 UI は P6-3。P6-1 は所属世帯 ≥1 を前提 |

---

## 1. 利用可能な RPC（P6-1 が触る契約）

すべて `RpcResult<T, RpcError>`。`RpcClientProvider.service<T>()` で取得。

| サービス | メソッド | 返り | 用途 |
|---|---|---|---|
| `HouseholdRpcService` | `list()` | `Households` | boot で所属世帯取得→`AppSession` |
| `ProductRpcService` | `list(householdId)` | `Stocks` | 在庫一覧 |
| `ProductRpcService` | `shoppingList(householdId)` | `ShoppingList` | 買い物リスト（P6-1b） |
| `StockRpcService` | `history(productId)` | `StockMovements` | 商品単位の履歴 |
| `StockRpcService` | `activity(householdId)` | `ActivityFeed` | 世帯の活動履歴（P6-1b） |
| `StockRegisterRpcService` | `replenish(productId, quantity, note)` | `Unit` | 補充 |
| `StockRegisterRpcService` | `consume(productId, quantity, note)` | `Unit` | 消費 |
| `StockRegisterRpcService` | `correct(target: MovementId, correctedQuantity, reason)` | `Unit` | 訂正 |
| `ProductRegisterRpcService` | `setWanted(productId, wanted)` | `Unit` | 手動希望（P6-1b） |

ドメイン型の要点（薄いマッピングでそのまま UI へ）:

- `Stock(product, movements)`：`currentQuantity()` / `status(): StockStatus(在庫切れ|残りわずか|十分)` / `product.name()` / `product.setting.minimumStock` / `product.setting.unit`。
- `StockMovement` sealed（`Replenishment` / `Consumption` / `Correction`）：`identity: MovementIdentity`（永続後は MovementId 取得可）/ `quantity` / `occurredAt` / `actor: Resident`（DisplayName を持つ＝履歴の「誰が」）/ `note`。`Correction` は `target` / `reason` を追加で持つ。
- 書込系は `RpcResult<Unit>`＝更新後の Stock を返さない。**成功後に `list()` / `history()` を再取得**してUI を更新する（セクション 3）。

---

## 2. live 配線スパイン（P6-1a の背骨・最優先）

P6-0 の placeholder（`App.kt` が `stock_placeholder` を表示）を実画面に差し替える。配線の流れ:

```text
AuthViewModel.boot()
  whoami() == Registered(resident)
   └ onAuthenticated(resident)                    （既存：AppSession に residentId/displayName）
   └ household.list() → Households                （新規：boot 後の世帯ロード）
       ├ 空        → AuthState.NeedHousehold（最小状態・P6-3 で本実装）
       └ 非空      → AppSession.setHouseholds(households, active = 先頭)
                     → AuthState.Ready
App() は AppSession.state を購読
  activeHouseholdId 確定後に AppShell(stockContent = { StockHomeRoute(householdId) })
    StockHomeRoute → InventoryViewModel(householdId, deps...) を remember
      InventoryViewModel ← InventoryRepository ← () -> rpc.service<XxxRpcService>()
```

### 2.1 世帯ロードの置き場所

- `AuthDeps` に `loadHouseholds(): Households`（または `onAuthenticated` 内で世帯ロードまで実施）を足し、`AuthViewModel.boot()` の Registered 分岐で `AppSession.setHouseholds(...)` まで行う。
- `AuthState` に `NeedHousehold`（世帯ゼロ）を追加。P6-1 では「世帯がありません」最小表示に倒し、世帯作成導線（P6-3）への placeholder を置く。
- アクティブ世帯は **先頭世帯**（`Households` の先頭）。世帯切替 UI は P6-3。

### 2.2 Repository DI（既存パターン踏襲）

`InventoryRepository` は接続済み単一 WS から lazy にサービスを取り出す「開く関数」を受ける（P6-0 で確立）:

```kotlin
InventoryRepository(
    productService = { rpc.service<ProductRpcService>() },
    stockService = { rpc.service<StockRpcService>() },          // 追加：history()
    stockRegisterService = { rpc.service<StockRegisterRpcService>() },
)
```

- ViewModel / Composable は `*RpcService` を直接触らない（`frontend-architecture.md`）。
- `householdId` は AppSession から渡す（画面が個別に持ち回らない）。

### 2.3 画面遷移（商品詳細）

- 商品詳細は同一 feature 内の状態遷移（モック同様、StockHome 上にスライドインするフルスクリーン）。Navigation Compose の型安全 route を使うか、`feature/inventory` 内の `selectedProduct` 状態で出すかは実装簡潔性で選ぶ（route は世帯横断ナビが増える P6-3 で本格化）。**P6-1a では feature 内状態で詳細を出す**（YAGNI）。

---

## 3. 書き込み→再取得 と トースト（横断）

書込系は `RpcResult<Unit>` のため、ViewModel が結果に応じて状態とトーストを更新する。

### 3.1 成功時

- 補充/消費/訂正の成功 → 該当世帯の `list()` を再フェッチして `InventoryUiState.Content` 更新（詳細表示中なら `history()` も再フェッチ）→ 成功トースト（例:「2個 補充しました」）。
- 楽観更新はしない（append-only / netQuantity 畳み込みはサーバ真実。再取得で確実に整合）。

### 3.2 エラー時（`RpcError` 網羅）

`userMessageOf(RpcError)`（`else` なし `when`、既存）で文言化し、表示先を使い分け:

| variant | 表示 |
|---|---|
| `BadRequest(field, reason)` | シート内フィールドエラー（数量・理由） |
| `Conflict(reason)` | トースト（在庫不足・訂正でマイナス等） |
| `Internal(reason)` | 汎用エラートースト（詳細は出さない） |
| `NotFound` | 文脈次第（一覧=空状態 / 詳細=エラー） |
| `Unauthorized` | セクション 4 の再認証導線へ |

### 3.3 トースト機構

- app 層に単一のトースト表示ホスト（Compose の `SnackbarHost` 相当）を置く。
- feature からの発火は軽量な `ToastController`（`StateFlow<ToastMessage?>` を公開、`show(message)` / 自動 dismiss）で行い、`MindstockTheme` 直下に host を 1 枚。
- designsystem に `Toast`/`Snackbar` atom を追加（Expressive のスタイルに寄せる）。文言は string resources。

---

## 4. `Unauthorized` → 再認証（横断・単一機構）

`Unauthorized` はどの feature repo からも出るため、画面ごとに処理せず**単一導線に集約**する（`frontend-rpc-and-error.md` の「P6-1 で配線」を満たす）。

```text
Repository → RpcOutcome.Failure(error) で error.requiresReauth() == true
  → ViewModel が共有 ReauthSignal を発火（または app へのコールバック）
    → app 受け口: TokenStore.clear() → RpcClientProvider.closeAll() → redirectToAuthorize()
```

- `RpcError.requiresReauth()` / `RpcClientProvider.closeAll()` は P6-0 で配線済みの dead surface。本フェーズで active 化。
- 受け口は app 層（`App.kt` か `AuthViewModel`）に 1 つ。feature ViewModel は「再認証が要る」というシグナルを上げるだけ（再認証の実行責務を持たない）。
- 実装形（共有 `SharedFlow<ReauthRequested>` か AuthViewModel コールバック注入か）は plan で確定。いずれにせよ **1 機構**。

---

## 5. 画面詳細（P6-1a）

mock 準拠。予測行/バッジ・日時ピッカー・通知 bell は除外。

### 5.1 StockHome（`feature/inventory/ui/StockHomeScreen.kt` 拡張）

- 挨拶ヘッダ（`こんにちは、{displayName}` / `在庫`）。displayName は AppSession。
- 検索バー：名前 substring filter（frontend 側。`Stocks` をローカル絞り込み）。ヒット 0 件は空状態（「商品を追加」導線）。
- grid/list 切替：既存 `SegmentedControl`。grid=`CompactCard`、list=`ProductCard`。
- カード：`Thumb` + 商品名 + `StatusDot`（status 色 = `LocalMindstockTokens`）+ 数量/単位 + `StockLevelBar` + 補充/消費クイックボタン。タップで詳細。
- 「商品を追加」ボタン：P6-2（商品追加）への placeholder（押下時トースト「準備中」等、active 化は P6-2）。
- desktop：`material3-adaptive` の幅で列数を増やす（mock の `columns`/`desktop` 相当）。shell は P6-0 の `NavigationSuiteScaffold`。

### 5.2 ProductDetail（`feature/inventory/ui` 新規）

- ヘッダ（戻る / owner なら設定アイコン＝P6-2 placeholder）。
- 数量大表示・`StatusDot`・最低在庫・`StockLevelBar`・補充/消費ボタン。
- **wanted トグルは P6-1a では出さない**（P6-1b で `setWanted` と共に）。
- **履歴**：`history(productId)` の `StockMovements` を新しい順に。各行 = 補充/消費アイコン + 数量 + `relTime` 相当 + actor DisplayName（`movement.actor.profile.displayName`）+ note + 訂正済バッジ + 「訂正」ボタン。`Correction` 行の扱い（打ち消し表示）は mock の `corrected` フラグに対応づける（実装で `StockMovements` の訂正畳み込みを参照）。

### 5.3 MoveSheet（補充/消費・`feature/inventory/ui` 新規）

- `Sheet` + 商品サマリ（`Thumb` + 現在数量）+ `Stepper`（数量）+ 現在→after プレビュー + メモ入力 + 確定ボタン。
- 消費で after<0 はサーバが `InsufficientStockException`→`Conflict`。UI 側でも after<0 を視覚警告（送信は許容しトーストで弾く、または送信ボタン無効化＝plan で確定）。

### 5.4 CorrectionSheet（訂正・`feature/inventory/ui` 新規）

- `Sheet` + 対象 movement 説明 + `Stepper`（訂正後数量）+ 理由入力（必須＝空なら確定無効）+ 確定 → `correct(target, qty, reason)`。
- `target` は履歴行の `MovementId`（`movement.identity` から取得）。

---

## 6. designsystem 追加（atom）

すべて designsystem 層に封じ込め、feature は atom 経由のみ（`frontend-designsystem.md`）。

| atom | 内容 |
|---|---|
| `Sheet` | モーダルボトムシート（Expressive motion、open/onClose/title）。mock の `Sheet` 相当 |
| `Stepper` | `−` / 数量 / `+`（単位表示）。Expressive のポップ感 |
| `Thumb` | 商品サムネ（icon または image、サイズ/角丸）。`ProductImage` 対応 |
| `RoundBtn` | 円形アイコンボタン（戻る/設定/bell 跡地など chrome 用） |
| `Toast` | スナックバー風通知（セクション 3.3） |
| `StockLevelBar.trackColor` | 既存 `StockLevelBar` に track 色引数を追加（holistic 送り） |

---

## 7. i18n / テスト / 検証

- UI 文言は `commonMain/composeResources/values/strings.xml`(ja) に追加（`frontend-i18n-and-font.md`）。コード直書き禁止。
- **非 Composable 層の文言**（`userMessageOf` 等）：P6-1 でトースト文言を UI 側が string resource に解決する薄い仕組みを導入し、literal 依存を縮小する（`frontend-i18n-and-font.md` の暫定例外を段階解消）。`userMessageOf` は「文言キー / 構造化結果」を返し、UI で `stringResource` 化する方向（実装形は plan で確定）。
- テスト（commonTest、Kotest FunSpec 不可・`kotlin.test.@Test` + Kotest assertions）:
  - InventoryViewModel：load / 書込成功→再取得 / エラー→トースト分岐 / `Unauthorized`→再認証シグナル / 検索 filter / 訂正 target 解決。
  - ProductDetailViewModel（新規）：history ロード / 訂正後再取得。
  - トースト/再認証の機構（`ToastController` / `ReauthSignal`）のロジック。
  - UI 描画網羅は追わない。
- 検証コマンド：`./gradlew :frontend:compileKotlinWasmJs`（フルビルドはローカルで OOM しうる）+ `:frontend:jsTest`（`--tests` フィルタ非対応＝全体実行）。live 検証は backend（Zitadel/Postgres）起動が要る。
- 実装は従来どおり **Subagent-Driven**（implementer + 各フェーズ spec/quality 2 段レビュー + 最終 holistic）。

---

## 8. 申し送り・非スコープ

- **登録直後の `rpc.connect()` 張り直し**（同一接続では handshake で session 固定のため、登録直後に `requireRegistered` メソッドを呼ぶと Unregistered のまま Unauthorized）は **オンボーディング（P6-3）の話**。P6-1 に到達したユーザは既に `whoami()=Registered` を通過し boot で張った単一接続を再利用するため P6-1 では発生しない（PR #110 申し送りの正しい帰属）。
- 商品追加 / 設定 / アーカイブ（`productRegister.adopt`/`addCustom`/`changeUnit`/`changeImage`/`changeMinimum`/`archive`/`unarchive`/`listArchived`）は **P6-2**。
- ログイン/オンボーディング・世帯切替/作成・招待・メンバー管理・設定は **P6-3**。
- 通知（bell / NotifSheet）は将来機能（OFF 固定・本実装しない）。

---

## 関連

- spec（前段）: [docs/superpowers/specs/2026-06-05-p6-0-frontend-foundation-design.md](2026-06-05-p6-0-frontend-foundation-design.md) — frontend 土台 + ルール
- spec（親）: [docs/superpowers/specs/2026-05-31-mindstock-full-replace-design/00-index.md](2026-05-31-mindstock-full-replace-design/00-index.md)
- plan（前段）: [docs/superpowers/plans/2026-06-06-ws-rpc-transport-redesign.md](../plans/2026-06-06-ws-rpc-transport-redesign.md) — 単一 `/api/rpc` / whoami
- mock: `docs/ref/mindstock.zip`（`app/screens-a..d.jsx`, `app/core.jsx`）
- rule（適用）: `frontend-architecture.md` / `frontend-rpc-and-error.md` / `frontend-designsystem.md` / `frontend-i18n-and-font.md` / `frontend-compose-conventions.md` / `frontend-kmp-structure.md` / `error-handling.md` / `testing.md`
