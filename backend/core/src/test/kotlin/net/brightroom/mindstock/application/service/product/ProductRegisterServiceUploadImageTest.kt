package net.brightroom.mindstock.application.service.product

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.product.ProductImageStorageRepository
import net.brightroom.mindstock.application.repository.product.ProductRegisterRepository
import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.HouseholdProfile
import net.brightroom.mindstock.domain.model.household.member.HouseholdMember
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.member.Members
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.image.ImageRef
import net.brightroom.mindstock.domain.model.inventory.product.image.ProductImage
import net.brightroom.mindstock.domain.model.inventory.product.image.RawImageUpload
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.ResidentProfile

class ProductRegisterServiceUploadImageTest :
    FunSpec({
        val productRepository = mockk<ProductRepository>()
        val productRegisterRepository = mockk<ProductRegisterRepository>(relaxed = true)
        val stockRepository = mockk<StockRepository>()
        val householdRepository = mockk<HouseholdRepository>()
        val imageStorage = mockk<ProductImageStorageRepository>()
        val service =
            ProductRegisterService(
                productRepository,
                productRegisterRepository,
                stockRepository,
                householdRepository,
                imageStorage,
            )
        val householdId = HouseholdId.create()
        val actor = ResidentId.create()

        fun householdWithActor(): Household {
            val resident = Resident(actor, ResidentProfile(DisplayName("たろう")))
            return Household(
                householdId,
                HouseholdProfile(HouseholdName("わが家")),
                Members(listOf(HouseholdMember(resident, HouseholdMemberRole.世帯主))),
            )
        }

        beforeTest {
            clearMocks(productRepository, productRegisterRepository, stockRepository, householdRepository, imageStorage)
        }

        test("uploadImage は store した ref を Stored 画像として appendRevision する") {
            val product = Product.custom(ProductName("水"), Barcode.Unlinked, ProductUnit("本"), MinimumStock(1))
            val upload = RawImageUpload(byteArrayOf(1, 2, 3))
            val ref = ImageRef("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
            every { productRepository.householdOf(product.id) } returns householdId
            every { householdRepository.findById(householdId) } returns householdWithActor()
            every { productRepository.findById(product.id) } returns product
            coEvery { imageStorage.store(upload) } returns ref

            service.uploadImage(product.id, upload, actor)

            coVerify { imageStorage.store(upload) }
            verify { productRegisterRepository.appendRevision(product.changeImage(ProductImage.Stored(ref))) }
        }
    })
