package net.brightroom.mindstock.application.service.product

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.brightroom.mindstock.application.repository.product.ProductRegisterRepository
import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.exception.DuplicateJanException
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit

class ProductRegisterServiceTest :
    FunSpec({
        val productRepository = mockk<ProductRepository>()
        val productRegisterRepository = mockk<ProductRegisterRepository>(relaxed = true)
        val stockRepository = mockk<StockRepository>()
        val service = ProductRegisterService(productRepository, productRegisterRepository, stockRepository)
        val householdId = HouseholdId.create()
        val jan = Jan("4901234567894")

        beforeTest {
            clearMocks(productRepository, productRegisterRepository, stockRepository)
        }

        test("採用済み JAN は DuplicateJanException で採用不可") {
            val item = CatalogItem(CatalogItemId.create(), jan, CatalogItemName("お茶"))
            every { productRepository.existsByJan(householdId, jan) } returns true
            shouldThrow<DuplicateJanException> {
                service.adopt(item, householdId, ProductUnit("個"), MinimumStock(1))
            }
            verify(exactly = 0) { productRegisterRepository.registerAdopted(any(), any(), any()) }
        }

        test("未採用 JAN は採用して登録する") {
            val item = CatalogItem(CatalogItemId.create(), jan, CatalogItemName("お茶"))
            every { productRepository.existsByJan(householdId, jan) } returns false
            val product = service.adopt(item, householdId, ProductUnit("個"), MinimumStock(1))
            verify { productRegisterRepository.registerAdopted(product, householdId, item.id) }
        }

        test("addCustom は Barcode.Linked のとき重複チェックする") {
            every { productRepository.existsByJan(householdId, jan) } returns true
            shouldThrow<DuplicateJanException> {
                service.addCustom(householdId, ProductName("自作"), Barcode.Linked(jan), ProductUnit("個"), MinimumStock(0))
            }
        }

        test("addCustom は Barcode.Unlinked なら重複チェックしない") {
            val product =
                service.addCustom(householdId, ProductName("自作"), Barcode.Unlinked, ProductUnit("個"), MinimumStock(0))
            verify { productRegisterRepository.registerCustom(product, householdId) }
            verify(exactly = 0) { productRepository.existsByJan(householdId, jan) }
        }
    })
