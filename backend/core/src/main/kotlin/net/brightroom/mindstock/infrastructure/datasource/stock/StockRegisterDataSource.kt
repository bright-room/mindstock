@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.stock

import net.brightroom.mindstock.application.repository.stock.StockRegisterRepository
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementIdentity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement
import net.brightroom.mindstock.infrastructure.datasource.schemas.StockMovementsTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class StockRegisterDataSource(
    private val database: Database,
) : StockRegisterRepository {
    override fun appendMovement(
        productId: ProductId,
        movement: StockMovement,
    ): StockMovement =
        transaction(database) {
            // HistoryTable は素の Table(IdTable ではない)。insertAndGetId は使えないため
            // `insert { } get id` で採番された Long を読み戻す。
            val newId: Long =
                StockMovementsTable.insert {
                    it[StockMovementsTable.productId] = productId()
                    it[kind] = movement.kindColumn()
                    it[quantity] = movement.quantity()
                    it[occurredAt] = movement.occurredAt()
                    it[actorResidentId] = movement.actor.id()
                    it[note] = movement.note()
                    if (movement is StockMovement.Correction) {
                        it[targetMovementId] = movement.target()
                        it[reason] = movement.reason()
                    }
                } get StockMovementsTable.id
            // 採番された id で Persisted に詰め直して返す
            rebindIdentity(movement, MovementIdentity.Persisted(MovementId(newId)))
        }

    /** movement の identity だけ Persisted に差し替えた新インスタンスを返す。copy() 不使用。 */
    private fun rebindIdentity(
        movement: StockMovement,
        identity: MovementIdentity.Persisted,
    ): StockMovement =
        when (movement) {
            is StockMovement.Replenishment -> {
                StockMovement.Replenishment(identity, movement.quantity, movement.occurredAt, movement.actor, movement.note)
            }

            is StockMovement.Consumption -> {
                StockMovement.Consumption(identity, movement.quantity, movement.occurredAt, movement.actor, movement.note)
            }

            is StockMovement.Correction -> {
                StockMovement.Correction(
                    identity,
                    movement.quantity,
                    movement.occurredAt,
                    movement.actor,
                    movement.note,
                    movement.target,
                    movement.reason,
                )
            }
        }
}
