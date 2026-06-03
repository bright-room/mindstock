package net.brightroom.mindstock.application.service.product

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.Products
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements

class ProductServiceTest :
    FunSpec({
        val stockRepository = mockk<StockRepository>()
        val productRepository = mockk<ProductRepository>()
        val service = ProductService(stockRepository, productRepository)
        val householdId = HouseholdId.create()

        test("shoppingList は手動希望フラグを Stock に突き合わせて合成する") {
            val wanted = Product.custom(ProductName("水"), Barcode.Unlinked, ProductUnit("本"), MinimumStock(5))
            val other = Product.custom(ProductName("米"), Barcode.Unlinked, ProductUnit("袋"), MinimumStock(1))
            every { stockRepository.listByHousehold(householdId) } returns
                Stocks(listOf(Stock(wanted, StockMovements(emptyList())), Stock(other, StockMovements(emptyList()))))
            every { productRepository.listWanted(householdId) } returns Products(listOf(wanted))

            val list = service.shoppingList(householdId)

            list.list.first { it.stock.product.id == wanted.id }.manuallyWanted shouldBe true
            list.list.first { it.stock.product.id == other.id }.manuallyWanted shouldBe false
        }
    })
