package net.brightroom.mindstock.application.service.stock

import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

class StockService(
    private val stockRepository: StockRepository,
    private val productRepository: ProductRepository,
    private val householdRepository: HouseholdRepository,
) {
    fun history(
        productId: ProductId,
        actor: ResidentId,
    ): StockMovements {
        val householdId = productRepository.householdOf(productId)
        householdRepository.findById(householdId).requireMember(actor)
        return stockRepository.historyOf(productId)
    }

    /** 世帯全体の活動履歴。Controller(P5c)が ActivityFeed に flatten する。 */
    fun activity(
        householdId: HouseholdId,
        actor: ResidentId,
    ): Stocks {
        householdRepository.findById(householdId).requireMember(actor)
        return stockRepository.listByHousehold(householdId)
    }
}
