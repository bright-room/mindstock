package net.brightroom.mindstock.application.service.stock

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.datetime.LocalDateTime
import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.resident.ResidentRepository
import net.brightroom.mindstock.application.repository.stock.StockRegisterRepository
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
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementIdentity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Reason
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile as ResidentProfile

class StockRegisterServiceTest :
    FunSpec({
        val residentRepository = mockk<ResidentRepository>()
        val stockRepository = mockk<StockRepository>()
        val stockRegisterRepository = mockk<StockRegisterRepository>(relaxed = true)
        val householdRepository = mockk<HouseholdRepository>()
        val productRepository = mockk<ProductRepository>()
        val service =
            StockRegisterService(
                residentRepository,
                stockRepository,
                stockRegisterRepository,
                householdRepository,
                productRepository,
            )

        val actor = Resident(ResidentId.create(), ResidentProfile(DisplayName("たろう")))
        val product = Product.custom(ProductName("水"), Barcode.Unlinked, ProductUnit("本"), MinimumStock(1))
        val householdId = HouseholdId.create()

        fun householdWithActor(): Household =
            Household(
                householdId,
                Profile(HouseholdName("わが家")),
                Members(listOf(HouseholdMember(actor, HouseholdMemberRole.世帯主))),
            )

        test("correct は findByMovement で対象を load し訂正 movement を append する") {
            val baseId = MovementId(1L)
            val base =
                StockMovement.Replenishment(
                    MovementIdentity.Persisted(baseId),
                    Quantity(5),
                    OccurredAt.now(),
                    actor,
                    Note(""),
                )
            every { productRepository.householdOf(product.id) } returns householdId
            every { householdRepository.findById(householdId) } returns householdWithActor()
            every { residentRepository.findById(actor.id) } returns actor
            every { stockRepository.findByMovement(baseId) } returns Stock(product, StockMovements(listOf(base)))

            val appended = slot<StockMovement>()
            every { stockRegisterRepository.appendMovement(product.id, capture(appended)) } returns base

            service.correct(baseId, Quantity(3), Reason("数え間違い"), actor.id)

            verify { stockRepository.findByMovement(baseId) }
            check(appended.captured is StockMovement.Correction) { "appended movement must be a Correction" }
        }

        test("replenish は渡された occurredAt をそのまま movement に記録する(バックデート)") {
            val backdated = OccurredAt(LocalDateTime(2026, 6, 1, 9, 0))
            val appended = slot<StockMovement>()
            every { productRepository.householdOf(product.id) } returns householdId
            every { householdRepository.findById(householdId) } returns householdWithActor()
            every { residentRepository.findById(actor.id) } returns actor
            every { stockRepository.findByProduct(product.id) } returns Stock(product, StockMovements(emptyList()))
            every { stockRegisterRepository.appendMovement(product.id, capture(appended)) } returns mockk(relaxed = true)

            service.replenish(product.id, Quantity(3), Note(""), backdated, actor.id)

            verify { stockRepository.findByProduct(product.id) }
            check(appended.captured is StockMovement.Replenishment) { "appended movement must be a Replenishment" }
            appended.captured.occurredAt shouldBe backdated
        }

        test("consume は findByProduct で対象を load し消費 movement を append する") {
            val seeded =
                StockMovement.Replenishment(
                    MovementIdentity.Persisted(MovementId(1L)),
                    Quantity(5),
                    OccurredAt.now(),
                    actor,
                    Note(""),
                )
            val appended = slot<StockMovement>()
            every { productRepository.householdOf(product.id) } returns householdId
            every { householdRepository.findById(householdId) } returns householdWithActor()
            every { residentRepository.findById(actor.id) } returns actor
            every { stockRepository.findByProduct(product.id) } returns Stock(product, StockMovements(listOf(seeded)))
            every { stockRegisterRepository.appendMovement(product.id, capture(appended)) } returns mockk(relaxed = true)

            service.consume(product.id, Quantity(2), Note(""), OccurredAt(LocalDateTime(2026, 6, 1, 9, 0)), actor.id)

            verify { stockRepository.findByProduct(product.id) }
            check(appended.captured is StockMovement.Consumption) { "appended movement must be a Consumption" }
        }

        test("replenish は product の世帯メンバーでなければ MembershipRequiredException") {
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
            shouldThrow<MembershipRequiredException> { service.replenish(product.id, Quantity(1), Note(""), OccurredAt.now(), actor.id) }
        }
    })
