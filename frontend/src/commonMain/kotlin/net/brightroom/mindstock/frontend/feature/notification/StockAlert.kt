package net.brightroom.mindstock.frontend.feature.notification

import net.brightroom.mindstock.domain.model.inventory.stock.Stock

/** ベル(お知らせ)に並ぶ在庫アラート 1 件。client 派生のビュー型。 */
data class StockAlert(
    val stock: Stock,
    val reason: AlertReason,
)

/** アラートの理由。mock NotifSheet のメッセージ分岐に対応。 */
sealed interface AlertReason {
    /** 在庫を切らしています(status=在庫切れ)。 */
    data object OutOfStock : AlertReason

    /** そろそろ補充どきです(status=残りわずか)。 */
    data object RunningLow : AlertReason

    /** あと約 days 日で切れそうです(status=十分 かつ 予測 <= 5 日)。 */
    data class RunningOutSoon(
        val days: Int,
    ) : AlertReason
}
