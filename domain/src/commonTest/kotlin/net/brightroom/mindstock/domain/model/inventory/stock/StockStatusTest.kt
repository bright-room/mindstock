package net.brightroom.mindstock.domain.model.inventory.stock

import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.quantity.NetQuantity
import kotlin.test.Test

class StockStatusTest {
    @Test
    fun ゼロ以下は在庫切れ() {
        StockStatus.of(NetQuantity(0), MinimumStock(3)) shouldBe StockStatus.在庫切れ
    }

    @Test
    fun 最低在庫数以下は残りわずか() {
        StockStatus.of(NetQuantity(3), MinimumStock(3)) shouldBe StockStatus.残りわずか
        StockStatus.of(NetQuantity(1), MinimumStock(3)) shouldBe StockStatus.残りわずか
    }

    @Test
    fun 最低在庫数を超えていれば十分() {
        StockStatus.of(NetQuantity(4), MinimumStock(3)) shouldBe StockStatus.十分
    }
}
