package net.brightroom.mindstock.application.repository.stock

import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement

interface StockRegisterRepository {
    /** stock_movements に 1 行 INSERT し、採番された id で Persisted な StockMovement を返す。 */
    fun appendMovement(
        productId: ProductId,
        movement: StockMovement,
    ): StockMovement
}
