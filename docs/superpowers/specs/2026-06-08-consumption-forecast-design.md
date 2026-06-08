# 消費予測「あと約X日」設計（トラックB #3）

P6-4b の backend gap 4 件のうち #3。モック `docs/ref/mindstock.zip` の「あと約X日」予測表示（在庫ホームの予測バナー／ProductCard、買い物リストの行・アラート）を、**実際の消費履歴から推定したレート**で再現する。

## 背景と現状

モックの予測は `app/data.jsx` の `predictDays(p) = round(p.qty / p.daily)`。ここで `p.daily` は **商品ごとの静的なシード定数**（例: `daily: 0.10`）で、履歴からは一切算出していない。実装にはこの定数が無いため、これまで予測表示を「消費ペースが domain に無く未実装」として落としていた（監査ドキュメント §1）。

縦スライスを調査した結果、予測は**新規インフラ不要・RPC 変更不要**で実装できる:

- ✅ `ProductRpcService.list()` は `Stocks`（= 各 `Stock` に `movements: StockMovements` 全件）を返す。`StockDataSource.listByHousehold` が履歴表示のため全 movement を既に hydrate しており、frontend は全商品の消費履歴を手元に持っている。
- ✅ 予測は movements を集計する純関数なので、`:domain`（common）に置けば **frontend が即利用**でき、追加クエリ 0 / 追加 payload 0。
- ✅ 同じドメイン関数を後の **#4 通知**（「在庫が切れそう」アラート）でバックエンドのスケジュール job が再利用できる。

## 確定事項（ユーザ承認済み 2026-06-08）

- **キャッシュしない・realtime（純ドメイン算出）**。`list()` が既に全 movement を送っているため、DB キャッシュ（`:backend:schedules` での定期計算）は追加クエリ・staleness・無効化ロジックを生むだけで、この項目ではむしろ重い。スケジュール計算が要るのは #4 通知であり、そこで本関数をバッチ時に都度再利用する（数値キャッシュではない）。
- **モックと数値は一致しない＝この項目に限り正常**。モックの `daily` は静的シード、実装は実履歴推定なので「あと約X日」の数字はモックと違ってよい。render-verify でこの数値の一致を狙わない（表示の有無・レイアウトのみ忠実化対象）。
- **推定窓 = トレーリング優先・全履歴 fallback**（下記アルゴリズム）。
- **`Unknown` は非表示**（モックの `predictDays` が `null` のとき要素を出さないのと同じ）。

### 将来トリガ（黙って落とさないための明示）

将来 `list()` の payload を絞るために**全 movement を送るのをやめる**最適化を入れる場合のみ、frontend で計算できなくなるため、そのとき初めて「read-model に forecast を server-side 算出して載せる（必要ならバッチキャッシュ）」が妥当になる。現状は movements を送っているので YAGNI。

## ドメインモデル

### `ConsumptionForecast`（新規 sealed・`:domain`）

予測結果。nullable 戻り値原則禁止に従い、「予測不可」を `null` でなく型で表す。

```kotlin
sealed interface ConsumptionForecast {
    /** 予測不可。消費実績が無い／現在在庫が 0 以下。 */
    data object Unknown : ConsumptionForecast
    /** 現在のペースであと約 days 日で在庫が尽きる見込み。 */
    data class DaysRemaining(val days: Int) : ConsumptionForecast
}
```

### `Stock.forecast(asOf: LocalDateTime): ConsumptionForecast`（新規メソッド）

現在数量（`currentQuantity()`）と `movements` の両方が要るので `Stock` に置く。`asOf` は引数注入し、ドメインは純粋に保つ（frontend は `:shared` の `LocalDateTime.now()`=JST、テストは固定値を渡す）。レート算出本体は `StockMovements` 側のヘルパ（例 `StockMovements.consumptionRatePerDay(asOf): Double` 系）に委譲してよいが、公開 API は `Stock.forecast` に集約する（レート単体は内部実装）。

## 推定アルゴリズム

記号:
- `qty` = `currentQuantity()`（訂正反映済みの現在在庫）。
- `W` = `60`（トレーリング窓・日数。名前付き定数 `FORECAST_WINDOW_DAYS`、調整可）。
- **消費量** = `Consumption` movement の effective quantity（`netQuantity()` と同一の訂正畳み込みを適用。`Replenishment` は数えない）。
- `totalConsumed` = 全期間の消費量合計。
- `spanDays` = `max(1, 最初の movement の occurredAt の日付 → asOf の日付 の日数)`（最初の movement は種別を問わず「観測開始」とみなす。`max(1, …)` で 0 除算回避）。
- `recentConsumed` = `(asOf - W日) 以降`に occurredAt を持つ消費量合計。

判定:

```
if (qty <= 0 || totalConsumed == 0)           -> Unknown
rate = if (spanDays >= W && recentConsumed > 0)
           recentConsumed / W                  // トレーリング（直近ペース）
       else
           totalConsumed / spanDays            // 全履歴 fallback（履歴が浅い or 直近窓に消費0）
days = round(qty / rate)
-> DaysRemaining(days)
```

設計意図:
- **トレーリング優先**で直近の消費ペースに追従する（古い大量消費に引きずられない）。
- **履歴が浅い新商品（`spanDays < W`）は全履歴 span ベース**にする。固定 `W=60` で割ると新商品のレートを過小評価し、予測日数が過大になるため（具体例 C 参照）。
- **直近窓に消費 0 でも全期間に消費があれば fallback** して数値を出す（モックの「常に何か表示」に寄せる）。

### 具体例（W=60、asOf 基準）

| 例 | 状況 | spanDays | recentConsumed | 経路 | rate | days |
|----|------|----------|----------------|------|------|------|
| **A 定常** | 120日前から利用・`qty=3`・直近60日に12消費 | 120 (≥W) | 12 (>0) | トレーリング | 12/60 = 0.20 | round(3/0.20)=**15** |
| **B 直近休止** | 100日前から・`qty=2`・消費は計5だが全て70〜90日前（直近60日は0） | 100 (≥W) | 0 | 全履歴 fallback | 5/100 = 0.05 | round(2/0.05)=**40** |
| **C 新商品** | 10日前に追加・`qty=4`・10日で6消費 | 10 (<W) | 6 | 全履歴(span) | 6/10 = 0.60 | round(4/0.60)≈6.67→**7** |
| **C′ 反例** | C を誤って `/W=60` で割ると | – | – | （誤）`6/60=0.10` | – | round(4/0.10)=40（過大）→ so span 採用が正しい |
| **D 消費なし** | 補充のみ・消費0 | – | – | – | – | **Unknown**（バナー非表示） |
| **E 在庫切れ** | `qty=0` | – | – | – | – | **Unknown**（モックは out 表示で `qty>0` のみ予測） |
| **F 訂正** | 消費3を後で1に訂正 | – | – | effective=1 で集計（`netQuantity` と同じ） | – | 訂正後の値で算出 |

## 日時の扱い（実装メモ）

`occurredAt` は JST 壁掛けの `LocalDateTime`。日数差・窓判定は `kotlinx-datetime` の `LocalDate` 解像度で行う（`firstOccurred.date.daysUntil(asOf.date)`、窓閾値 `asOf.date.minus(DatePeriod(days = W))`）。家庭用途の疎なデータでは日単位解像度で十分。

## frontend 配線

`:domain` に `Stock.forecast(asOf)` がある前提で、表示箇所を実配線する（`asOf` は `LocalDateTime.now()`）:

- **StockHome**（`feature/inventory/ui/StockHomeScreen.kt`）
  - SummaryStrip 予測バナー: 在庫 > 0 で `DaysRemaining` の中から最小 days の商品を選び「◯◯ はあと約X日で切れる予測です」。該当無しならバナー非表示。
  - ProductCard: `DaysRemaining` のとき「· あと約X日」。`Unknown` は非表示。
- **ShoppingList**（`feature/shopping/ui/ShoppingListScreen.kt`）
  - 行の「あと約X日」（`DaysRemaining` かつ在庫 > 0）。
  - アラート文言「あと約X日で切れそうです」（`out`/`low` 以外で `DaysRemaining` のとき）。

ViewModel/Repository の RPC 形は不変（`Stocks` をそのまま受け、表示時に `forecast` を呼ぶ）。

## テスト

- `:domain` commonTest（`Stock` / `StockMovements`）: 上表の各分岐を固定 `asOf` で検証 — A トレーリング / B fallback / C 新商品 span / D 消費なし=Unknown / E 在庫0=Unknown / F 訂正反映 / 0 除算回避（`spanDays` 1日クランプ）/ 窓境界（ちょうど W 日前の消費の内外）。
- frontend: 表示の有無のみ（`Unknown` で要素が出ない・`DaysRemaining` で文言が出る）。数値そのものは検証対象にしない（モック不一致が正常なため）。

## スコープ外

- レート窓 `W` のユーザ設定化・商品別カスタム（v1 は固定定数）。
- 予測の信頼区間・ばらつき表示。
- `:backend:schedules` でのバッチ計算／DB キャッシュ（#4 通知で本関数を再利用する際に扱う）。

## 関連

- 監査: `docs/superpowers/specs/2026-06-08-p6-4b-fidelity-audit.md` §1, §5
- メモリ: `p6-4b-fidelity-program`（backend gap #3）
- 後続: #4 通知が `Stock.forecast()` をバッチ時に再利用
