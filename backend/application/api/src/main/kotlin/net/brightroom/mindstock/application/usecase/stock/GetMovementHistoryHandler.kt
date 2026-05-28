package net.brightroom.mindstock.application.usecase.stock

import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.movement.StockMovements

class GetMovementHistoryHandler(
    private val stockRepository: StockRepository,
) {
    fun handle(
        product: Product,
        limit: Int = 50,
    ): StockMovements = stockRepository.movementHistory(product, limit)
}
