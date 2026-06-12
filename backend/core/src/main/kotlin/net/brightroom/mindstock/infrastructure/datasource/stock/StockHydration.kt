@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.stock

import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementIdentity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Reason
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.infrastructure.datasource.schemas.MovementKind
import net.brightroom.mindstock.infrastructure.datasource.schemas.StockMovementsTable
import org.jetbrains.exposed.v1.core.ResultRow

/** stock_movements 行 + 解決済み actor から StockMovement(sealed)を組み立てる。 */
internal fun ResultRow.toStockMovement(actor: Resident): StockMovement {
    val identity = MovementIdentity.Persisted(MovementId(this[StockMovementsTable.id]))
    val quantity = Quantity(this[StockMovementsTable.quantity])
    val occurredAt = OccurredAt(this[StockMovementsTable.occurredAt])
    val note = Note(this[StockMovementsTable.note])
    return when (this[StockMovementsTable.kind]) {
        MovementKind.REPLENISHMENT -> {
            StockMovement.Replenishment(identity, quantity, occurredAt, actor, note)
        }

        MovementKind.CONSUMPTION -> {
            StockMovement.Consumption(identity, quantity, occurredAt, actor, note)
        }

        MovementKind.CORRECTION -> {
            val movementId = this[StockMovementsTable.id]
            StockMovement.Correction(
                identity,
                quantity,
                occurredAt,
                actor,
                note,
                target =
                    MovementId(
                        this[StockMovementsTable.targetMovementId]
                            ?: error("corrupted correction movement $movementId: target_movement_id is null"),
                    ),
                reason =
                    Reason(
                        this[StockMovementsTable.reason]
                            ?: error("corrupted correction movement $movementId: reason is null"),
                    ),
            )
        }
    }
}

/** StockMovement → kind 列値。 */
internal fun StockMovement.kindColumn(): MovementKind =
    when (this) {
        is StockMovement.Replenishment -> MovementKind.REPLENISHMENT
        is StockMovement.Consumption -> MovementKind.CONSUMPTION
        is StockMovement.Correction -> MovementKind.CORRECTION
    }
