package net.brightroom.mindstock.domain.model.inventory.product.setting

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.inventory.quantity.NetQuantity
import kotlin.test.Test

class MinimumStockTest {
    @Test
    fun 負の値は拒否する() {
        shouldThrow<IllegalArgumentException> { MinimumStock(-1) }
    }

    @Test
    fun 現在数が最低在庫数以下なら閾値を下回っている() {
        MinimumStock(3).isBelow(NetQuantity(3)) shouldBe true
        MinimumStock(3).isBelow(NetQuantity(2)) shouldBe true
    }

    @Test
    fun 現在数が最低在庫数を超えていれば閾値を下回っていない() {
        MinimumStock(3).isBelow(NetQuantity(4)) shouldBe false
    }

    @Test
    fun 不足数は最低在庫数との差でゼロ未満はゼロに丸める() {
        MinimumStock(3).shortage(NetQuantity(1)) shouldBe 2
        MinimumStock(3).shortage(NetQuantity(5)) shouldBe 0
    }
}
