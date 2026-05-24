package net.brightroom.mindstock.application.usecase.stock

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.stock.Stock
import net.brightroom.mindstock.domain.repository.stock.StockRepository

class ListStocksHandler(
    private val stockRepository: StockRepository,
) {
    fun handle(household: Household): List<Stock> = stockRepository.stocksOf(household)
}
