package net.brightroom.mindstock.infrastructure.datasource.stock

import net.brightroom.mindstock.domain.model.stock.movement.Consumption
import net.brightroom.mindstock.domain.model.stock.movement.Note
import net.brightroom.mindstock.domain.model.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.stock.movement.Quantity
import net.brightroom.mindstock.domain.model.stock.movement.Replenishment
import net.brightroom.mindstock.domain.model.stock.movement.StockMovement
import net.brightroom.mindstock.domain.model.user.profile.Profile
import kotlin.time.Instant

internal fun toStockMovement(
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
        StockMovementType.REPLENISHMENT -> Replenishment(q, occurred, actor, n)
        StockMovementType.CONSUMPTION -> Consumption(q, occurred, actor, n)
    }
}
