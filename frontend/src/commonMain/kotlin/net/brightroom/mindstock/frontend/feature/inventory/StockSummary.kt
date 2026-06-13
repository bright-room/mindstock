package net.brightroom.mindstock.frontend.feature.inventory

import net.brightroom.mindstock.domain.model.inventory.stock.StockStatus

/** 在庫サマリ(買い物 CTA 用)。needCount = 在庫切れ + 残りわずか + 手動希望(want)。 */
data class StockSummary(
    val outCount: Int,
    val lowCount: Int,
    val wantCount: Int = 0,
) {
    val needCount: Int get() = outCount + lowCount + wantCount
}

fun stockSummaryOf(
    statuses: List<StockStatus>,
    wantCount: Int = 0,
): StockSummary =
    StockSummary(
        outCount = statuses.count { it == StockStatus.在庫切れ },
        lowCount = statuses.count { it == StockStatus.残りわずか },
        wantCount = wantCount,
    )
