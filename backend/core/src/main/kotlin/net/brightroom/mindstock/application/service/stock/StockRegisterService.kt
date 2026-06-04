package net.brightroom.mindstock.application.service.stock

import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.resident.ResidentRepository
import net.brightroom.mindstock.application.repository.stock.StockRegisterRepository
import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Reason
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

class StockRegisterService(
    private val residentRepository: ResidentRepository,
    private val stockRepository: StockRepository,
    private val stockRegisterRepository: StockRegisterRepository,
    private val householdRepository: HouseholdRepository,
    private val productRepository: ProductRepository,
) {
    private fun authorizeProduct(
        productId: ProductId,
        actor: ResidentId,
    ) = householdRepository.findById(productRepository.householdOf(productId)).requireMember(actor)

    fun replenish(
        productId: ProductId,
        quantity: Quantity,
        note: Note,
        actor: ResidentId,
    ) {
        authorizeProduct(productId, actor)
        val resident = residentRepository.findById(actor)
        val stock = stockRepository.findByProduct(productId)
        val replenished = stock.replenish(quantity, OccurredAt.now(), resident, note)
        stockRegisterRepository.appendMovement(productId, replenished.latestMovement())
    }

    fun consume(
        productId: ProductId,
        quantity: Quantity,
        note: Note,
        actor: ResidentId,
    ) {
        authorizeProduct(productId, actor)
        val resident = residentRepository.findById(actor)
        val stock = stockRepository.findByProduct(productId)
        val consumed = stock.consume(quantity, OccurredAt.now(), resident, note)
        stockRegisterRepository.appendMovement(productId, consumed.latestMovement())
    }

    /** RPC correct は productId を受けない。MovementId から Stock を丸ごと load して訂正する。 */
    fun correct(
        target: MovementId,
        correctedQuantity: Quantity,
        reason: Reason,
        actor: ResidentId,
    ) {
        val stock = stockRepository.findByMovement(target)
        householdRepository.findById(productRepository.householdOf(stock.product.id)).requireMember(actor)
        val resident = residentRepository.findById(actor)
        val corrected = stock.correct(target, correctedQuantity, reason, resident, OccurredAt.now())
        stockRegisterRepository.appendMovement(stock.product.id, corrected.latestMovement())
    }
}
