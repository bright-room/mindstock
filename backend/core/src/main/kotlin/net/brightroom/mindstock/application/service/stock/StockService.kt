package net.brightroom.mindstock.application.service.stock

import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements

class StockService(
    private val stockRepository: StockRepository,
) {
    fun history(productId: ProductId): StockMovements = stockRepository.historyOf(productId)

    /** 世帯全体の活動履歴。Controller(P5c)が ActivityFeed に flatten する。 */
    fun activity(householdId: HouseholdId): Stocks = stockRepository.listByHousehold(householdId)
}
