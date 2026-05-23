package net.brightroom.mindstock.domain.model.stock.movement

import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.user.User

data class Replenishment(
    override val product: Product,
    override val quantity: Quantity,
    override val occurredAt: OccurredAt,
    override val actor: User,
    override val note: Note,
) : StockMovement {
    override val type: StockMovementType get() = StockMovementType.REPLENISHMENT
}
