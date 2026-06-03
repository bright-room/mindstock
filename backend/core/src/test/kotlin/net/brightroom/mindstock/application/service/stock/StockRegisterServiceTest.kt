package net.brightroom.mindstock.application.service.stock

import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.brightroom.mindstock.application.repository.resident.ResidentRepository
import net.brightroom.mindstock.application.repository.stock.StockRegisterRepository
import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.model.barcode.Barcode
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
import net.brightroom.mindstock.domain.model.resident.profile.Profile

class StockRegisterServiceTest :
    FunSpec({
        val residentRepository = mockk<ResidentRepository>()
        val stockRepository = mockk<StockRepository>()
        val stockRegisterRepository = mockk<StockRegisterRepository>(relaxed = true)
        val service = StockRegisterService(residentRepository, stockRepository, stockRegisterRepository)

        val actor = Resident(ResidentId.create(), Profile(DisplayName("たろう")))
        val product = Product.custom(ProductName("水"), Barcode.Unlinked, ProductUnit("本"), MinimumStock(1))

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
            every { residentRepository.findById(actor.id) } returns actor
            every { stockRepository.findByMovement(baseId) } returns Stock(product, StockMovements(listOf(base)))

            val appended = slot<StockMovement>()
            every { stockRegisterRepository.appendMovement(product.id, capture(appended)) } returns base

            service.correct(baseId, Quantity(3), Reason("数え間違い"), actor.id)

            verify { stockRepository.findByMovement(baseId) }
            check(appended.captured is StockMovement.Correction) { "appended movement must be a Correction" }
        }

        test("replenish は findByProduct で対象を load し補充 movement を append する") {
            val appended = slot<StockMovement>()
            every { residentRepository.findById(actor.id) } returns actor
            every { stockRepository.findByProduct(product.id) } returns Stock(product, StockMovements(emptyList()))
            every { stockRegisterRepository.appendMovement(product.id, capture(appended)) } returns mockk(relaxed = true)

            service.replenish(product.id, Quantity(3), Note(""), actor.id)

            verify { stockRepository.findByProduct(product.id) }
            check(appended.captured is StockMovement.Replenishment) { "appended movement must be a Replenishment" }
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
            every { residentRepository.findById(actor.id) } returns actor
            every { stockRepository.findByProduct(product.id) } returns Stock(product, StockMovements(listOf(seeded)))
            every { stockRegisterRepository.appendMovement(product.id, capture(appended)) } returns mockk(relaxed = true)

            service.consume(product.id, Quantity(2), Note(""), actor.id)

            verify { stockRepository.findByProduct(product.id) }
            check(appended.captured is StockMovement.Consumption) { "appended movement must be a Consumption" }
        }
    })
