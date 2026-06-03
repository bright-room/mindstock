package net.brightroom.mindstock.application.repository.stock

import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements

interface StockRepository {
    fun findByProduct(productId: ProductId): Stock

    /** 当該 movement を含む Stock を丸ごと返す(correct 用)。不在は ResourceNotFoundException。 */
    fun findByMovement(movementId: MovementId): Stock

    /** 世帯の在庫一覧(採用中商品。activity 組み立ては P5)。 */
    fun listByHousehold(householdId: HouseholdId): Stocks

    /** 1 商品の movement 全件(history)。 */
    fun historyOf(productId: ProductId): StockMovements
}
