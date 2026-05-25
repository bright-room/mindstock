package net.brightroom.mindstock.domain.model.stock.movement

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.user.User

@Serializable
data class Consumption(
    override val product: Product,
    override val quantity: Quantity,
    override val occurredAt: OccurredAt,
    override val actor: User,
    override val note: Note,
) : StockMovement {
    @Transient
    override val type: StockMovementType = StockMovementType.CONSUMPTION
}
