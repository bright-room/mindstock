package net.brightroom.mindstock.application.usecase.stock

import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Stock

class GetStockHandler(
    private val stockRepository: StockRepository,
) {
    fun handle(product: Product): Stock = stockRepository.stockOf(product)
}
