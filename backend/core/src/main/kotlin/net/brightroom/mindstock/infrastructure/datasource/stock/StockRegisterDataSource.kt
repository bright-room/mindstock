@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.stock

import net.brightroom.mindstock.application.repository.stock.StockRegisterRepository
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
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
    ) {
        transaction(database) {
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
            }
        }
    }
}
