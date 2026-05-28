package net.brightroom.mindstock.application.repository.stock

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Stock
import net.brightroom.mindstock.domain.model.stock.movement.StockMovements

interface StockRepository {
    /** 1 商品の在庫状態。 */
    fun stockOf(product: Product): Stock

    /** 世帯全商品の在庫状態(ShoppingList 用)。 */
    fun stocksOf(household: Household): List<Stock>

    /** 指定商品の movement 履歴(最新順を想定)。 */
    fun movementHistory(
        product: Product,
        limit: Int = 50,
    ): StockMovements
}
