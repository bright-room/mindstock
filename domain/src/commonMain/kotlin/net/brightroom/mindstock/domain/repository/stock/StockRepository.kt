package net.brightroom.mindstock.domain.repository.stock

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Stock
import net.brightroom.mindstock.domain.model.stock.consumption.Consumptions
import net.brightroom.mindstock.domain.model.stock.replenishment.Replenishments

interface StockRepository {
    /** 1 商品の在庫状態。 */
    fun stockOf(product: Product): Stock

    /** 世帯全商品の在庫状態(ShoppingList 用)。 */
    fun stocksOf(household: Household): List<Stock>

    fun replenishmentHistory(product: Product, limit: Int = 50): Replenishments

    fun consumptionHistory(product: Product, limit: Int = 50): Consumptions
}
