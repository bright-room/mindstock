package net.brightroom.mindstock.application.usecase.stock

import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Stock
import net.brightroom.mindstock.domain.repository.stock.StockRepository

class GetStockHandler(
    private val stockRepository: StockRepository,
) {
    fun handle(product: Product): Stock = stockRepository.stockOf(product)
}
