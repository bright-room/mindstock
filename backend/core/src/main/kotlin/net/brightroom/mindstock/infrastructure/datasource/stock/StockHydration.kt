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
import net.brightroom.mindstock.infrastructure.datasource.schemas.StockMovementsTable
import org.jetbrains.exposed.v1.core.ResultRow

/** stock_movements 行 + 解決済み actor から StockMovement(sealed)を組み立てる。 */
internal fun ResultRow.toStockMovement(actor: Resident): StockMovement {
    val identity = MovementIdentity.Persisted(MovementId(this[StockMovementsTable.id]))
    val quantity = Quantity(this[StockMovementsTable.quantity])
    val occurredAt = OccurredAt(this[StockMovementsTable.occurredAt])
    val note = Note(this[StockMovementsTable.note])
    return when (this[StockMovementsTable.kind]) {
        StockMovementsTable.KIND_REPLENISHMENT -> {
            StockMovement.Replenishment(identity, quantity, occurredAt, actor, note)
        }

        StockMovementsTable.KIND_CONSUMPTION -> {
            StockMovement.Consumption(identity, quantity, occurredAt, actor, note)
        }

        StockMovementsTable.KIND_CORRECTION -> {
            StockMovement.Correction(
                identity,
                quantity,
                occurredAt,
                actor,
                note,
                target = MovementId(this[StockMovementsTable.targetMovementId]!!),
                reason = Reason(this[StockMovementsTable.reason]!!),
            )
        }

        else -> {
            error("unknown movement kind: ${this[StockMovementsTable.kind]}")
        }
    }
}

/** StockMovement → kind リテラル。 */
internal fun StockMovement.kindColumn(): String =
    when (this) {
        is StockMovement.Replenishment -> StockMovementsTable.KIND_REPLENISHMENT
        is StockMovement.Consumption -> StockMovementsTable.KIND_CONSUMPTION
        is StockMovement.Correction -> StockMovementsTable.KIND_CORRECTION
    }
