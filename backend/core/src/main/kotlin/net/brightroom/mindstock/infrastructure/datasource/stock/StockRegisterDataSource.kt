package net.brightroom.mindstock.infrastructure.datasource.stock

import net.brightroom.mindstock.application.repository.stock.StockRegisterRepository
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.movement.Note
import net.brightroom.mindstock.domain.model.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.stock.movement.Quantity
import net.brightroom.mindstock.domain.model.user.UserId
import org.jetbrains.exposed.v1.jdbc.insert
import java.time.ZoneOffset
import kotlin.time.toJavaInstant
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class StockRegisterDataSource : StockRegisterRepository {
    override fun replenish(
        product: Product,
        quantity: Quantity,
        occurredAt: OccurredAt,
        by: UserId,
        note: Note,
    ) {
        insertMovement(product, quantity, occurredAt, by, note, StockMovementType.REPLENISHMENT)
    }

    override fun consume(
        product: Product,
        quantity: Quantity,
        occurredAt: OccurredAt,
        by: UserId,
        note: Note,
    ) {
        insertMovement(product, quantity, occurredAt, by, note, StockMovementType.CONSUMPTION)
    }

    private fun insertMovement(
        product: Product,
        quantity: Quantity,
        occurredAt: OccurredAt,
        actor: UserId,
        note: Note,
        type: StockMovementType,
    ) {
        StockMovementsTable.insert {
            it[product_id] = product.id()
            it[StockMovementsTable.type] = type
            it[StockMovementsTable.quantity] = quantity()
            it[occurred_at] = occurredAt().toJavaInstant().atOffset(ZoneOffset.UTC)
            it[acted_by] = actor()
            it[StockMovementsTable.note] = note()
        }
    }
}
