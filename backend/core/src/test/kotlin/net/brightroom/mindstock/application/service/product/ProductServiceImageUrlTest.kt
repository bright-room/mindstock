package net.brightroom.mindstock.application.service.product

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.product.ProductImageStorageRepository
import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.image.ImageRef
import net.brightroom.mindstock.domain.model.inventory.product.image.ImageUrl
import net.brightroom.mindstock.domain.model.inventory.product.image.ProductImage
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit

class ProductServiceImageUrlTest :
    FunSpec({
        val stockRepository = mockk<StockRepository>()
        val productRepository = mockk<ProductRepository>()
        val householdRepository = mockk<HouseholdRepository>()
        val imageStorage = mockk<ProductImageStorageRepository>()
        val service = ProductService(stockRepository, productRepository, householdRepository, imageStorage)

        test("imageUrl は Stored 画像なら presigned URL を返す") {
            val ref = ImageRef("households/x/products/y.jpg")
            val product =
                Product
                    .custom(ProductName("水"), Barcode.Unlinked, ProductUnit("本"), MinimumStock(1))
                    .changeImage(ProductImage.Stored(ref))
            val url = ImageUrl("https://example.com/y.jpg?sig=abc")
            every { productRepository.findById(product.id) } returns product
            coEvery { imageStorage.presignedUrl(ref) } returns url

            service.imageUrl(product.id) shouldBe url

            coVerify { imageStorage.presignedUrl(ref) }
        }

        test("imageUrl は None 画像なら ResourceNotFoundException") {
            val product = Product.custom(ProductName("水"), Barcode.Unlinked, ProductUnit("本"), MinimumStock(1))
            every { productRepository.findById(product.id) } returns product

            shouldThrow<ResourceNotFoundException> { service.imageUrl(product.id) }
        }
    })
