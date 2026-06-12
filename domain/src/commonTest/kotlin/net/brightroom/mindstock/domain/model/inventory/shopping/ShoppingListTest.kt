package net.brightroom.mindstock.domain.model.inventory.shopping

import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.ProductStatus
import net.brightroom.mindstock.domain.model.inventory.product.image.ProductImage
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.product.setting.StockingPolicy
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.ResidentProfile
import kotlin.test.Test

class ShoppingListTest {
    private fun actor() = Resident(ResidentId.create(), ResidentProfile(DisplayName("おや")))

    private fun stock(
        name: String,
        minimum: Int,
        quantity: Int,
    ): Stock {
        val product =
            Product(
                id = ProductId.create(),
                name = ProductName(name),
                barcode = Barcode.Unlinked,
                setting = StockingPolicy(ProductUnit("個"), MinimumStock(minimum)),
                image = ProductImage.None,
                status = ProductStatus.採用中,
            )
        val base = Stock(product, StockMovements(emptyList()))
        return if (quantity > 0) base.replenish(Quantity(quantity), OccurredAt.now(), actor(), Note("")) else base
    }

    @Test
    fun 自動アイテムと手動アイテムを正しく分類する() {
        val shortage = ShoppingEntry(stock("米", minimum = 3, quantity = 1), manuallyWanted = Wanted(false)) // 在庫不足
        val manual = ShoppingEntry(stock("醤油", minimum = 1, quantity = 5), manuallyWanted = Wanted(true)) // 十分だが手動
        val neither = ShoppingEntry(stock("お茶", minimum = 1, quantity = 5), manuallyWanted = Wanted(false)) // 十分・不要
        val list = ShoppingList(listOf(shortage, manual, neither))

        list.autoItems().list shouldBe listOf(shortage)
        list.manualItems().list shouldBe listOf(manual)
        list.size() shouldBe 3
    }

    @Test
    fun エントリの必要度とリスト掲載フラグを返す() {
        val entry = ShoppingEntry(stock("米", minimum = 3, quantity = 1), manuallyWanted = Wanted(false))
        entry.need() shouldBe ShoppingNeed.在庫不足
        entry.onList() shouldBe true
    }
}
