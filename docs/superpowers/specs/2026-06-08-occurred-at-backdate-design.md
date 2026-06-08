# occurredAt バックデート設計（トラックB #1）

P6-4b の backend gap 4 件のうち #1。補充 / 消費の「いつの出来事か」をユーザが指定（バックデート）できるようにする。モック `docs/ref/mindstock.zip`（`screens-b.jsx:MoveSheet` / `DatePick`）の挙動を再現する。

## 背景と現状

モックの `MoveSheet` には日時ピッカー（`DatePick`）があり、補充/消費を過去日として記録できる。実装側はこれを「サーバ時刻確定」として落としていた（`MoveSheet` KDoc に「日時ピッカーは無し=サーバ時刻確定」と明記）。本設計でこの決定を**覆す**。

縦スライスを調査した結果、これは新規インフラではなく**薄いスレッドスルー**である:

- ✅ Domain `Stock.replenish/consume/correct` は既に `OccurredAt` を引数に取る
- ✅ DB `stock_movements.occurred_at` 列は既存・永続化済み（`StockRegisterDataSource` が `movement.occurredAt()` を書き込む）
- ❌ 唯一の欠落: `StockRegisterService` が `OccurredAt.now()` をハードコードし、RPC/Controller が `occurredAt` を通していない。frontend `MoveSheet` に日時ピッカーが無い

## 確定事項（モック忠実化に基づく決定）

- **訂正（correct）はバックデートしない**。モックの `CorrectionSheet` は `onSubmit(qty, reason)` のみで日時ピッカーが無い。訂正は「今」記録するシステムイベントとしてサーバ側 `OccurredAt.now()` を維持する。
- **`OccurredAt` VO に未来日制約は入れない**（無制約のまま）。未来日の抑止は frontend のピッカー UI 側（選択可能日を「今日以前」に制限）で行い、サーバ検証は入れない（クライアント時計のズレで「今日」が弾かれる事故を避ける）。
- **カレンダーボタンは本物の M3 `DatePicker` を配線する**（モックでは onClick 無しの装飾スタブだったが、ユーザ判断で機能化）。

## 変更内容

### 1. RPC interface（`rpc/.../stock/StockRegisterRpcService.kt`）

`replenish` / `consume` に位置引数 `occurredAt: OccurredAt` を追加。既存の位置引数スタイルに合わせ、Request 型は作らない。`correct` は変更なし。

```kotlin
suspend fun replenish(
    productId: ProductId,
    quantity: Quantity,
    note: Note,
    occurredAt: OccurredAt,
): RpcResult<Unit, RpcError>
```

KDoc の「occurredAt は…サーバ時刻由来」記述を更新する。

### 2. Controller（`backend/api/.../stock/StockRegisterController.kt`）

`occurredAt` を受けて Service に素通しする。

### 3. Service（`backend/core/.../stock/StockRegisterService.kt`）

`replenish` / `consume` が `occurredAt: OccurredAt` を受け取り、ハードコードの `OccurredAt.now()` を削除して引数を `stock.replenish(...)` / `stock.consume(...)` に渡す。`correct` は `OccurredAt.now()` 維持。

### 4. Domain

変更なし。`OccurredAt` VO も変更なし（未来日制約を入れない方針）。

### 5. Frontend（`frontend/.../feature/inventory/ui/MoveSheet.kt` ほか）

- `MoveSheet` にメモ欄の上へ `DatePick` 行を追加:
  - 3 チップ「今日 / 昨日 / おととい」（モック `screens-b.jsx:DatePick` の実数値: height 42 / radius 12 / `600 13.5px` / active 時 accent ボーダー + accentSoft 背景）
  - カレンダーボタン（46×42 / radius 12）→ タップで M3 `DatePicker`。`SelectableDates` で「今日以前」のみ選択可。
- 選択値の意味: 選んだ日付 + 現在のローカル時刻（チップの `now − N日` を一般化）。`OccurredAt` を frontend で構築（`:shared` の `LocalDateTime.now()` 利用、`OccurredAt(localDateTime)`）。
- `onSubmit` を `(quantity, note, occurredAt)` に変更し、`occurredAt` を inventory ViewModel → Repository → `StockRegisterRpcService.replenish/consume` まで通す。
- designsystem に必要なら `DatePick` atom / `DatePicker` ラッパを追加（feature 層は material3 を直接 import しない規約に従う）。

### 6. ドキュメント / ルールの覆し

「occurredAt = サーバ確定」の記述を更新する:
- `MoveSheet.kt` の KDoc
- `StockRegisterRpcService.kt` の KDoc
- 関連 spec の該当文（実装時に grep して特定）

### 7. テスト

- `StockRegisterServiceTest` / `StockRegisterControllerTest` を新シグネチャに更新。
- バックデートのアサーション追加: 過去の `occurredAt` を渡すと `now` で上書きされず、その値が永続化されること。

## 範囲外

- 画像 / 消費予測 / 通知（トラックB の別 PR）
- 訂正のバックデート（モックに無い）

## 検証

- backend: `./gradlew test`（Service / Controller テスト）
- frontend: `./gradlew :frontend:compileKotlinWasmJs` でコンパイル、`:frontend:jsBrowserDevelopmentRun` で MoveSheet を render してモック `screens-b.jsx` と同寸 side-by-side で DatePick の忠実度確認
