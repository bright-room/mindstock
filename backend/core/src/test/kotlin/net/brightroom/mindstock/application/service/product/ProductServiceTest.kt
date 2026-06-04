package net.brightroom.mindstock.application.service.product

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.exception.MembershipRequiredException
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.Profile
import net.brightroom.mindstock.domain.model.household.member.HouseholdMember
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.member.Members
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.Products
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile as ResidentProfile

class ProductServiceTest :
    FunSpec({
        val stockRepository = mockk<StockRepository>()
        val productRepository = mockk<ProductRepository>()
        val householdRepository = mockk<HouseholdRepository>()
        val service = ProductService(stockRepository, productRepository, householdRepository)

        val actor = ResidentId.create()
        val member = Resident(actor, ResidentProfile(DisplayName("じぶん")))
        val householdId = HouseholdId.create()

        fun householdWith(vararg residents: Resident) =
            Household(
                householdId,
                Profile(HouseholdName("わが家")),
                Members(residents.map { HouseholdMember(it, HouseholdMemberRole.世帯主) }),
            )

        test("list はメンバーなら在庫一覧を返す") {
            every { householdRepository.findById(householdId) } returns householdWith(member)
            every { stockRepository.listByHousehold(householdId) } returns Stocks(emptyList())
            service.list(householdId, actor) shouldBe Stocks(emptyList())
        }

        test("list は非メンバーなら MembershipRequiredException") {
            every { householdRepository.findById(householdId) } returns
                householdWith(Resident(ResidentId.create(), ResidentProfile(DisplayName("ほかのひと"))))
            shouldThrow<MembershipRequiredException> { service.list(householdId, actor) }
        }

        test("shoppingList は手動希望フラグを Stock に突き合わせて合成する") {
            val wanted = Product.custom(ProductName("水"), Barcode.Unlinked, ProductUnit("本"), MinimumStock(5))
            val other = Product.custom(ProductName("米"), Barcode.Unlinked, ProductUnit("袋"), MinimumStock(1))
            every { householdRepository.findById(householdId) } returns householdWith(member)
            every { stockRepository.listByHousehold(householdId) } returns
                Stocks(listOf(Stock(wanted, StockMovements(emptyList())), Stock(other, StockMovements(emptyList()))))
            every { productRepository.listWanted(householdId) } returns Products(listOf(wanted))

            val list = service.shoppingList(householdId, actor)

            list.list.first { it.stock.product.id == wanted.id }.manuallyWanted shouldBe true
            list.list.first { it.stock.product.id == other.id }.manuallyWanted shouldBe false
        }
    })
