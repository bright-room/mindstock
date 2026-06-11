package net.brightroom.mindstock.application.service.product

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.product.ProductImageStorageRepository
import net.brightroom.mindstock.application.repository.product.ProductRegisterRepository
import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.exception.DuplicateJanException
import net.brightroom.mindstock.domain.exception.MembershipRequiredException
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.Profile
import net.brightroom.mindstock.domain.model.household.member.HouseholdMember
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.member.Members
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile as ResidentProfile

class ProductRegisterServiceTest :
    FunSpec({
        val productRepository = mockk<ProductRepository>()
        val productRegisterRepository = mockk<ProductRegisterRepository>(relaxed = true)
        val stockRepository = mockk<StockRepository>()
        val householdRepository = mockk<HouseholdRepository>()
        val imageStorage = mockk<ProductImageStorageRepository>()
        val service =
            ProductRegisterService(productRepository, productRegisterRepository, stockRepository, householdRepository, imageStorage)
        val householdId = HouseholdId.create()
        val actor = ResidentId.create()
        val jan = Jan("4901234567894")

        fun householdWithActor(): Household {
            val resident = Resident(actor, ResidentProfile(DisplayName("たろう")))
            return Household(
                householdId,
                Profile(HouseholdName("わが家")),
                Members(listOf(HouseholdMember(resident, HouseholdMemberRole.世帯主))),
            )
        }

        beforeTest {
            clearMocks(productRepository, productRegisterRepository, stockRepository, householdRepository)
        }

        test("採用済み JAN は DuplicateJanException で採用不可") {
            val item = CatalogItem(CatalogItemId.create(), jan, CatalogItemName("お茶"))
            every { householdRepository.findById(householdId) } returns householdWithActor()
            every { productRepository.existsByJan(householdId, jan) } returns true
            shouldThrow<DuplicateJanException> {
                service.adopt(item, householdId, ProductUnit("個"), MinimumStock(1), actor)
            }
            verify(exactly = 0) { productRegisterRepository.registerAdopted(any(), any(), any()) }
        }

        test("未採用 JAN は採用して登録する") {
            val item = CatalogItem(CatalogItemId.create(), jan, CatalogItemName("お茶"))
            every { householdRepository.findById(householdId) } returns householdWithActor()
            every { productRepository.existsByJan(householdId, jan) } returns false
            val product = service.adopt(item, householdId, ProductUnit("個"), MinimumStock(1), actor)
            verify { productRegisterRepository.registerAdopted(product, householdId, item.id) }
        }

        test("addCustom は Barcode.Linked のとき重複チェックする") {
            every { householdRepository.findById(householdId) } returns householdWithActor()
            every { productRepository.existsByJan(householdId, jan) } returns true
            shouldThrow<DuplicateJanException> {
                service.addCustom(householdId, ProductName("自作"), Barcode.Linked(jan), ProductUnit("個"), MinimumStock(0), actor)
            }
        }

        test("addCustom は Barcode.Unlinked なら重複チェックしない") {
            every { householdRepository.findById(householdId) } returns householdWithActor()
            val product =
                service.addCustom(householdId, ProductName("自作"), Barcode.Unlinked, ProductUnit("個"), MinimumStock(0), actor)
            verify { productRegisterRepository.registerCustom(product, householdId) }
            verify(exactly = 0) { productRepository.existsByJan(householdId, jan) }
        }

        test("changeUnit は product の世帯メンバーでなければ MembershipRequiredException") {
            val product =
                net.brightroom.mindstock.domain.model.inventory.product.Product.custom(
                    ProductName("水"),
                    Barcode.Unlinked,
                    ProductUnit("本"),
                    MinimumStock(1),
                )
            every { productRepository.householdOf(product.id) } returns householdId
            every { householdRepository.findById(householdId) } returns
                Household(
                    householdId,
                    Profile(HouseholdName("わが家")),
                    Members(
                        listOf(
                            HouseholdMember(
                                Resident(ResidentId.create(), ResidentProfile(DisplayName("ほか"))),
                                HouseholdMemberRole.世帯主,
                            ),
                        ),
                    ),
                )
            shouldThrow<MembershipRequiredException> { service.changeUnit(product.id, ProductUnit("缶"), actor) }
        }
    })
