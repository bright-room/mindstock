package net.brightroom.mindstock.application.repository.stock

import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement

interface StockRegisterRepository {
    /** stock_movements に 1 行 INSERT する。 */
    fun appendMovement(
        productId: ProductId,
        movement: StockMovement,
    )
}
