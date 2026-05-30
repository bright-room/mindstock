package net.brightroom.mindstock.application.service.stock

import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Stock
import net.brightroom.mindstock.domain.model.stock.Stocks
import net.brightroom.mindstock.domain.model.stock.movement.StockMovements

class StockService(
    private val stockRepository: StockRepository,
) {
    suspend fun get(product: Product): Stock = stockRepository.stockOf(product)

    suspend fun list(household: Household): Stocks = stockRepository.stocksOf(household)

    suspend fun getMovementHistory(
        product: Product,
        limit: Int = 50,
    ): StockMovements = stockRepository.movementHistory(product, limit)
}
