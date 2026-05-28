package net.brightroom.mindstock.infrastructure.datasource.repository.stock

import net.brightroom.mindstock.application.repository.stock.StockRegisterRepository
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.movement.Consumption
import net.brightroom.mindstock.domain.model.stock.movement.Replenishment
import net.brightroom.mindstock.domain.model.stock.movement.StockMovementType
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.infrastructure.datasource.stock.StockMovementsTable
import org.jetbrains.exposed.v1.jdbc.insert
import java.time.ZoneOffset
import kotlin.time.toJavaInstant
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
internal class StockRegisterRepositoryImpl : StockRegisterRepository {
    override fun replenish(
        product: Product,
        quantity: Quantity,
        occurredAt: OccurredAt,
        by: User,
        note: Note,
    ): Replenishment {
        insertMovement(product, quantity, occurredAt, by, note, StockMovementType.REPLENISHMENT)
        return Replenishment(product, quantity, occurredAt, by, note)
    }

    override fun consume(
        product: Product,
        quantity: Quantity,
        occurredAt: OccurredAt,
        by: User,
        note: Note,
    ): Consumption {
        insertMovement(product, quantity, occurredAt, by, note, StockMovementType.CONSUMPTION)
        return Consumption(product, quantity, occurredAt, by, note)
    }

    private fun insertMovement(
        product: Product,
        quantity: Quantity,
        occurredAt: OccurredAt,
        actor: User,
        note: Note,
        type: StockMovementType,
    ) {
        StockMovementsTable.insert {
            it[product_id] = product.id()
            it[StockMovementsTable.type] = type
            it[StockMovementsTable.quantity] = quantity()
            it[occurred_at] = occurredAt().toJavaInstant().atOffset(ZoneOffset.UTC)
            it[acted_by] = actor.id()
            it[StockMovementsTable.note] = note()
        }
    }
}
