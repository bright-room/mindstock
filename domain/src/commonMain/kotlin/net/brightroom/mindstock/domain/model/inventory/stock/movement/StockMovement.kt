package net.brightroom.mindstock.domain.model.inventory.stock.movement

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.resident.Resident

@Serializable
sealed interface StockMovement {
    val identity: MovementIdentity
    val quantity: Quantity
    val occurredAt: OccurredAt
    val actor: Resident
    val note: Note
}

@Serializable
data class Replenishment(
    override val identity: MovementIdentity,
    override val quantity: Quantity,
    override val occurredAt: OccurredAt,
    override val actor: Resident,
    override val note: Note,
) : StockMovement

@Serializable
data class Consumption(
    override val identity: MovementIdentity,
    override val quantity: Quantity,
    override val occurredAt: OccurredAt,
    override val actor: Resident,
    override val note: Note,
) : StockMovement

@Serializable
data class Correction(
    override val identity: MovementIdentity,
    override val quantity: Quantity,
    override val occurredAt: OccurredAt,
    override val actor: Resident,
    override val note: Note,
    val target: MovementId,
    val reason: Reason,
) : StockMovement
