# 在庫アラート通知(お知らせ / NotifSheet)設計

> P6-4b トラック B gap #4「通知(ベル/お知らせ/Web Push)」の **scope ① = モック忠実 / client 派生** 版。
> ユーザ決定(2026-06-13): アプリ起動中にベルを押すと在庫アラート一覧が出る、までを実装する。
> **サーバ側通知の永続化・定期生成バッチ・Web Push 配信は今回作らない**(モック自身も Web Push を「将来機能」として設定トグル `disabled` で描いている)。

## Goal

在庫ホーム等のベル🔔をタップすると「お知らせ」シート(`NotifSheet`)が開き、**在庫切れ / 残りわずか / もうすぐ切れそう**な商品の一覧(先頭 6 件)が表示される。行タップで該当商品の詳細へ遷移。ベルのバッジは「アラートが 1 件以上あれば点灯」する。挙動・見た目はモック(`docs/ref/mindstock.zip` の `app/screens-c.jsx` `NotifSheet`)に忠実。

## Background / 現状

- ベルは present-but-no-op。`StockHomeScreen.kt:167` の `NavIconButton(icon = Bell, onClick = {}, badge = true)`(バッジは常時点灯のハードコード)、デスクトップは `WideShell` のサイドバー項目に `onBell` があるが `App.kt:441` で `onBell = {}`。
- 通知に必要なドメイン API は **既に揃っている**(backend 追加不要):
  - `Stock.status(): StockStatus`(`在庫切れ` / `残りわずか` / `十分`)
  - `Stock.forecast(asOf: EvaluatedTime): ConsumptionForecast`(`DaysRemaining(days)` / `Unknown`。#3 で実装済)
  - 在庫は `InventoryViewModel`(`feature/inventory`)がロード済みで保持。
- 設定画面の Web Push トグルは **既に disabled で実装済み**(`SettingsScreen.kt:164` `settings_pref_push` / `settings_pref_push_sub`、`Toggle(checked = false, enabled = false)`)。モックの disabled トグルと一致しており **変更しない**。

## モックの正(`app/screens-c.jsx` NotifSheet / `app/data.jsx`)

- アラート対象: `statusOf(p) !== 'ok' || (predictDays(p) !== null && d <= 5 && p.qty > 0)`、`.slice(0, 6)`。
  - `statusOf`: `qty<=0`→out / `qty<=min`→low / それ以外→ok
  - `predictDays`: `round(qty/daily)`、消費ペースが無ければ無し
- メッセージ(状態優先): out → `在庫を切らしています` / low → `そろそろ補充どきです` / それ以外(ok かつ forecast≤5) → `あと約${d}日で切れそうです`
- 各行: 左に status 色のアイコン箱(out は `cart`、それ以外 `trend`、38×38・radius 11・status.soft 背景 / status.color)、商品名(600/14・省略)、状態文(500/12・faint)、右端 chevron。行タップ = シートを閉じて商品詳細を開く。
- シート見出し下に副文(400/12・faint): `在庫減少のお知らせ（将来は Web Push で端末に通知）`。

### ドメインへの対応(モック条件の写像)

`Stock.forecast()` は在庫 0 以下のとき `Unknown` を返すため、`DaysRemaining` が出る時点で「在庫 > 0」が保証される。よってモックの `p.qty > 0` ガードは自動的に満たされ、別途チェック不要。優先度は status を先に見る(out/low は forecast によらず status メッセージ)。

## アーキテクチャ

backend 変更ゼロ。frontend のみ。新規 feature パッケージ `feature/notification/` を切り、在庫由来のアラート導出ロジックと `NotifSheet` を置く(モックが "Notifications" として別グルーピングしているのに倣う)。データ源は `InventoryViewModel` が保持する `Stocks` 単一ソース。

### 新規ファイル(`feature/notification/`)

1. **`StockAlert.kt`** — アラートのモデル(domain VO をそのまま抱える薄いビュー型)
   ```kotlin
   data class StockAlert(
       val stock: Stock,
       val reason: AlertReason,
   )

   sealed interface AlertReason {
       data object OutOfStock : AlertReason            // 在庫を切らしています
       data object RunningLow : AlertReason            // そろそろ補充どきです
       data class RunningOutSoon(val days: Int) : AlertReason  // あと約X日で切れそうです
   }
   ```

2. **`StockAlerts.kt`** — 純関数
   ```kotlin
   fun stockAlerts(stocks: Stocks, asOf: EvaluatedTime): List<StockAlert> =
       stocks.list.mapNotNull { stock ->
           when (val status = stock.status()) {
               StockStatus.在庫切れ -> StockAlert(stock, AlertReason.OutOfStock)
               StockStatus.残りわずか -> StockAlert(stock, AlertReason.RunningLow)
               StockStatus.十分 -> when (val f = stock.forecast(asOf)) {
                   is ConsumptionForecast.DaysRemaining ->
                       if (f() <= 5) StockAlert(stock, AlertReason.RunningOutSoon(f())) else null
                   ConsumptionForecast.Unknown -> null
               }
           }
       }.take(6)
   ```
   - `mapNotNull` 内のローカルラムダの `null` は idiomatic(公開 API シグネチャの nullable ではない)。
   - 純ロジックなのでテスト対象。

3. **`ui/NotifSheet.kt`** — `Sheet` atom 構成の Composable
   ```kotlin
   @Composable
   fun NotifSheet(
       open: Boolean,
       alerts: List<StockAlert>,
       onClose: () -> Unit,
       onOpen: (Stock) -> Unit,
   )
   ```
   - `Sheet(open, title = stringResource(notif_title), onClose)` の中に副文 + アラート行リスト。
   - 行は `designsystem/atom`(`AppIcon` / `AppText` / `StatusDot` 等)と `LocalMindstockTokens`(status 色)で組む。feature は Material3 を直接使わない(`frontend-designsystem` ルール)。アイコンは out → `AppIconName.Cart`、それ以外 → `AppIconName.Trend`、右端 `AppIconName.ChevronRight`。
   - 行タップで `onClose()` してから `onOpen(stock)`。
   - alerts が空でも副文は出す(モックは常に副文表示。空配列なら行が無いだけ)。

### 配線(既存修正)

- **`App.kt`(`ReadyContent`)**:
  - `val homeState by homeVm.state.collectAsState()`
  - 世帯確定時に eager load(`LaunchedEffect(householdId) { homeVm.load() }`)— **全タブで**バッジ/シートが正しくなるよう、Stock タブに入る前にロードする。`InventoryRoute` 内の既存 load/refresh はそのまま(再読込は idempotent で害なし)。
  - `val alerts = (homeState as? InventoryUiState.Content)?.let { stockAlerts(it.stocks, EvaluatedTime.now()) } ?: emptyList()`
  - `var notifOpen by remember { mutableStateOf(false) }`(世帯切替時 false に)
  - `onBell = { notifOpen = true }` を `AppShell` に渡す(デスクトップのサイドバーベル)。
  - `InventoryRoute` に `onBell = { notifOpen = true }` と `hasAlerts = alerts.isNotEmpty()` を渡す(モバイルヘッダのベル)。
  - overlay として `NotifSheet(open = notifOpen, alerts = alerts, onClose = { notifOpen = false }, onOpen = { stock -> notifOpen = false; opened.value = DetailTarget(stock.product.id, stock) })` を配置。
- **`InventoryRoute`**: `onBell: () -> Unit` と `hasAlerts: Boolean` 引数を追加し `StockHomeScreen` へ素通し。
- **`StockHomeScreen.kt:167`**: ベルを `onClick = onBell`、`badge = hasAlerts`(ハードコード `true` を解消)。`StockHomeScreen` に `onBell` / `hasAlerts` 引数追加。
- デスクトップ(`WideShell`)のサイドバーベルにはモック通りドット(badge)を付けない。`onBell` を繋ぐだけ。

### i18n(`commonMain/composeResources/values/strings.xml` 追加)

| key | 文言 |
|---|---|
| `notif_title` | `お知らせ` |
| `notif_subtitle` | `在庫減少のお知らせ（将来は Web Push で端末に通知）` |
| `notif_alert_out` | `在庫を切らしています` |
| `notif_alert_low` | `そろそろ補充どきです` |
| `notif_alert_soon` | `あと約%1$d日で切れそうです` |

`AlertReason` → 文言の写像は `NotifSheet`(Composable)側で `stringResource` する(`UiText` を経由しない。ViewModel を持たない純表示のため)。

## エラーハンドリング

- 通信なし(在庫は既存 `InventoryViewModel` のロードに相乗り)。アラート導出は純関数で例外を投げない。
- 在庫ロード失敗(`InventoryUiState.Error`)時は alerts 空 → バッジ消灯・シートは行なし。既存の在庫ホームのエラー表示はそのまま。
- nullable 戻り値は公開境界に出さない(`stockAlerts` は `List<StockAlert>` を返す)。

## テスト

- **`StockAlertsTest`**(commonTest・`kotlin.test.@Test` + Kotest assertions。FunSpec 不可):
  - 在庫切れ → `OutOfStock`、残りわずか → `RunningLow`、十分+`DaysRemaining(≤5)` → `RunningOutSoon(days)`、十分+`DaysRemaining(>5)` / `Unknown` → 除外。
  - status 優先(out/low は forecast によらず status reason)。
  - 7 件以上で先頭 6 件に切り詰め。
  - 空 `Stocks` → 空リスト。
- UI 描画網羅は追わない(`frontend-compose-conventions`)。`NotifSheet` の見た目はモック突合(render verify)で確認。

## 検証(忠実度)

- `:frontend:jsBrowserDevelopmentRun` で `NotifSheet` をモックと side-by-side render verify(`fidelity-verify-loop-mechanics`)。アイコン箱の status 色・行の余白/radius・副文の色とサイズを実数値突合。
- ベルのバッジが在庫状況で点灯/消灯すること、行タップで商品詳細が開くことを実機で確認。

## スコープ外(明示・後出し防止)

- **サーバ側通知の永続化・定期生成バッチ(`:backend:schedules`)・Web Push 配信(VAPID / Service Worker / subscription)** は今回作らない。将来 scope ②/③ として別 spec で扱う余地を残す。
- 設定画面の Web Push トグル(既存 disabled)は変更しない。
- 既読/未読モデルは持たない(バッジ = 現在アラートの有無)。

## 関連

- memory: `p6-4b-fidelity-program`(gap #4)/ `full-replace-2026-06` / `fidelity-verify-loop-mechanics`
- mock: `docs/ref/mindstock.zip`(`app/screens-c.jsx` NotifSheet / `app/data.jsx` statusOf・predictDays)
- 既存実装: `Stock.forecast`(#3・PR #125)/ `feature/inventory`(InventoryViewModel・StockHomeScreen)
