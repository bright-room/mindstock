package net.brightroom.mindstock.domain.model.inventory.shopping

import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.inventory.stock.StockStatus
import kotlin.test.Test

class ShoppingNeedTest {
    @Test
    fun insufficient_stock_needs_shopping() {
        ShoppingNeed.judge(StockStatus.在庫切れ, manuallyWanted = false) shouldBe ShoppingNeed.在庫不足
        ShoppingNeed.judge(StockStatus.残りわずか, manuallyWanted = false) shouldBe ShoppingNeed.在庫不足
    }

    @Test
    fun enough_but_manually_wanted() {
        ShoppingNeed.judge(StockStatus.十分, manuallyWanted = true) shouldBe ShoppingNeed.手動希望
    }

    @Test
    fun enough_and_not_wanted_is_unnecessary() {
        ShoppingNeed.judge(StockStatus.十分, manuallyWanted = false) shouldBe ShoppingNeed.不要
    }

    @Test
    fun onShoppingList_flag() {
        ShoppingNeed.在庫不足.onShoppingList shouldBe true
        ShoppingNeed.手動希望.onShoppingList shouldBe true
        ShoppingNeed.不要.onShoppingList shouldBe false
    }
}
