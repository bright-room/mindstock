package net.brightroom.mindstock.domain.model.inventory.stock

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.exception.CannotArchiveWithStockException
import net.brightroom.mindstock.domain.exception.InsufficientStockException
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementIdentity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Reason
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement.Consumption
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement.Correction
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement.Replenishment
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.resident.Resident

@Serializable
data class Stock(
    val product: Product,
    val movements: StockMovements,
) {
    fun currentQuantity(): Int = movements.netQuantity()

    fun status(): StockStatus = StockStatus.of(currentQuantity(), product.setting.minimumStock)

    fun replenish(
        quantity: Quantity,
        occurredAt: OccurredAt,
        actor: Resident,
        note: Note,
    ): Stock = Stock(product, movements.add(Replenishment(MovementIdentity.Pending, quantity, occurredAt, actor, note)))

    fun consume(
        quantity: Quantity,
        occurredAt: OccurredAt,
        actor: Resident,
        note: Note,
    ): Stock {
        if (currentQuantity() < quantity()) {
            throw InsufficientStockException("cannot consume $quantity from stock of ${currentQuantity()}")
        }
        return Stock(product, movements.add(Consumption(MovementIdentity.Pending, quantity, occurredAt, actor, note)))
    }

    fun correct(
        target: MovementId,
        correctedQuantity: Quantity,
        reason: Reason,
        actor: Resident,
        occurredAt: OccurredAt,
    ): Stock {
        if (!movements.hasBaseMovement(target)) {
            throw ResourceNotFoundException("movement not found: $target")
        }
        val corrected =
            movements.add(
                Correction(
                    identity = MovementIdentity.Pending,
                    quantity = correctedQuantity,
                    occurredAt = occurredAt,
                    actor = actor,
                    note = Note(""),
                    target = target,
                    reason = reason,
                ),
            )
        if (corrected.netQuantity() < 0) {
            throw InsufficientStockException("correction would make stock negative: ${corrected.netQuantity()}")
        }
        return Stock(product, corrected)
    }

    fun archive(): Stock {
        if (!Archivability.of(currentQuantity()).archivable) {
            throw CannotArchiveWithStockException("cannot archive with stock: ${currentQuantity()}")
        }
        return Stock(product.archive(), movements)
    }

    fun unarchive(): Stock = Stock(product.unarchive(), movements)

    /** 直近に追記した movement(replenish/consume/correct 後にこれを永続化する)。movement が無ければ ResourceNotFoundException。 */
    fun latestMovement(): StockMovement = movements.list.lastOrNull() ?: throw ResourceNotFoundException("no movement")
}
