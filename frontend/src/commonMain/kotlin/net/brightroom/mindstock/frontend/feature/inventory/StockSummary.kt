package net.brightroom.mindstock.frontend.feature.inventory

import net.brightroom.mindstock.domain.model.inventory.stock.StockStatus

/** 在庫サマリ(買い物 CTA 用)。needCount = 在庫切れ + 残りわずか。 */
data class StockSummary(
    val outCount: Int,
    val lowCount: Int,
) {
    val needCount: Int get() = outCount + lowCount
}

fun stockSummaryOf(statuses: List<StockStatus>): StockSummary =
    StockSummary(
        outCount = statuses.count { it == StockStatus.在庫切れ },
        lowCount = statuses.count { it == StockStatus.残りわずか },
    )
