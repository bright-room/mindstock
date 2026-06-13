package net.brightroom.mindstock.frontend.feature.inventory

import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.inventory.stock.StockStatus
import kotlin.test.Test

class StockSummaryTest {
    @Test
    fun counts_out_and_low_from_statuses() {
        val summary = stockSummaryOf(listOf(StockStatus.在庫切れ, StockStatus.在庫切れ, StockStatus.残りわずか, StockStatus.十分))
        summary.outCount shouldBe 2
        summary.lowCount shouldBe 1
        summary.needCount shouldBe 3
    }

    @Test
    fun all_sufficient_has_zero_need() {
        val summary = stockSummaryOf(listOf(StockStatus.十分, StockStatus.十分))
        summary.needCount shouldBe 0
    }

    @Test
    fun need_count_includes_manual_want() {
        // out 1 + low 1 + want 2 = need 4(手動希望は status=十分でも need に加算される)。
        val summary = stockSummaryOf(listOf(StockStatus.在庫切れ, StockStatus.残りわずか, StockStatus.十分, StockStatus.十分), wantCount = 2)
        summary.outCount shouldBe 1
        summary.lowCount shouldBe 1
        summary.wantCount shouldBe 2
        summary.needCount shouldBe 4
    }
}
