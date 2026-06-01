package net.brightroom.mindstock.domain.model.inventory.shopping

import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.inventory.stock.StockStatus
import kotlin.test.Test

class ShoppingNeedTest {
    @Test
    fun 在庫不足のステータスは買い物が必要() {
        ShoppingNeed.judge(StockStatus.在庫切れ, manuallyWanted = false) shouldBe ShoppingNeed.在庫不足
        ShoppingNeed.judge(StockStatus.残りわずか, manuallyWanted = false) shouldBe ShoppingNeed.在庫不足
    }

    @Test
    fun 十分でも手動希望なら手動希望になる() {
        ShoppingNeed.judge(StockStatus.十分, manuallyWanted = true) shouldBe ShoppingNeed.手動希望
    }

    @Test
    fun 十分かつ手動希望なしなら不要() {
        ShoppingNeed.judge(StockStatus.十分, manuallyWanted = false) shouldBe ShoppingNeed.不要
    }

    @Test
    fun 在庫不足と手動希望はリスト掲載フラグが真() {
        ShoppingNeed.在庫不足.onShoppingList shouldBe true
        ShoppingNeed.手動希望.onShoppingList shouldBe true
        ShoppingNeed.不要.onShoppingList shouldBe false
    }
}
