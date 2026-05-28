package net.brightroom.mindstock.application.usecase.stock

import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.stock.Stock

class ListStocksHandler(
    private val stockRepository: StockRepository,
) {
    fun handle(household: Household): List<Stock> = stockRepository.stocksOf(household)
}
