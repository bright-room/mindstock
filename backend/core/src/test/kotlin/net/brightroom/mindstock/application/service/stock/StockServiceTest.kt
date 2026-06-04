package net.brightroom.mindstock.application.service.stock

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.exception.MembershipRequiredException
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.Profile
import net.brightroom.mindstock.domain.model.household.member.HouseholdMember
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.member.Members
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile as ResidentProfile

class StockServiceTest :
    FunSpec({
        val stockRepository = mockk<StockRepository>()
        val productRepository = mockk<ProductRepository>()
        val householdRepository = mockk<HouseholdRepository>()
        val service = StockService(stockRepository, productRepository, householdRepository)

        val actor = ResidentId.create()
        val member = Resident(actor, ResidentProfile(DisplayName("じぶん")))
        val householdId = HouseholdId.create()
        val productId = ProductId.create()

        fun householdWith(vararg residents: Resident) =
            Household(householdId, Profile(HouseholdName("わが家")), Members(residents.map { HouseholdMember(it, HouseholdMemberRole.世帯主) }))

        test("activity はメンバーなら在庫一覧を返す") {
            every { householdRepository.findById(householdId) } returns householdWith(member)
            every { stockRepository.listByHousehold(householdId) } returns Stocks(emptyList())
            service.activity(householdId, actor) shouldBe Stocks(emptyList())
        }

        test("history は product の世帯を解決して非メンバーを弾く") {
            every { productRepository.householdOf(productId) } returns householdId
            every { householdRepository.findById(householdId) } returns
                householdWith(Resident(ResidentId.create(), ResidentProfile(DisplayName("ほか"))))
            shouldThrow<MembershipRequiredException> { service.history(productId, actor) }
        }

        test("history はメンバーなら movement 履歴を返す") {
            every { productRepository.householdOf(productId) } returns householdId
            every { householdRepository.findById(householdId) } returns householdWith(member)
            every { stockRepository.historyOf(productId) } returns StockMovements(emptyList())
            service.history(productId, actor) shouldBe StockMovements(emptyList())
        }
    })
