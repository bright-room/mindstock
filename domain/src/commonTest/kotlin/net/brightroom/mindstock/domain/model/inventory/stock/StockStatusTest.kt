package net.brightroom.mindstock.domain.model.inventory.stock

import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import kotlin.test.Test

class StockStatusTest {
    @Test
    fun zero_or_less_is_out_of_stock() {
        StockStatus.of(0, MinimumStock(3)) shouldBe StockStatus.在庫切れ
    }

    @Test
    fun at_or_under_minimum_is_low() {
        StockStatus.of(3, MinimumStock(3)) shouldBe StockStatus.残りわずか
        StockStatus.of(1, MinimumStock(3)) shouldBe StockStatus.残りわずか
    }

    @Test
    fun above_minimum_is_enough() {
        StockStatus.of(4, MinimumStock(3)) shouldBe StockStatus.十分
    }
}
