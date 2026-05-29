package net.brightroom.mindstock.infrastructure.datasource.stock

import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.movement.Consumption
import net.brightroom.mindstock.domain.model.stock.movement.Replenishment
import net.brightroom.mindstock.domain.model.stock.movement.StockMovement
import net.brightroom.mindstock.domain.model.stock.movement.StockMovementType
import net.brightroom.mindstock.domain.model.user.profile.Profile
import kotlin.time.Instant

internal fun toStockMovement(
    product: Product,
    actor: Profile,
    type: StockMovementType,
    quantity: Int,
    occurredAt: Instant,
    note: String,
): StockMovement {
    val q = Quantity(quantity)
    val occurred = OccurredAt(occurredAt)
    val n = Note(note)
    return when (type) {
        StockMovementType.REPLENISHMENT -> Replenishment(product, q, occurred, actor, n)
        StockMovementType.CONSUMPTION -> Consumption(product, q, occurred, actor, n)
    }
}
