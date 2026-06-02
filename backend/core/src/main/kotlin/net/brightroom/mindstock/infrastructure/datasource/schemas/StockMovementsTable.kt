@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.datetime

object StockMovementsTable : Table("stock_movements") {
    val id = long("id").autoIncrement()
    override val primaryKey = PrimaryKey(id)

    val productId = reference("product_id", ProductsTable.id, onDelete = ReferenceOption.RESTRICT)
    val kind = varchar("kind", 20) // REPLENISHMENT / CONSUMPTION / CORRECTION
    val quantity = integer("quantity")
    val occurredAt = datetime("occurred_at")
    val actorResidentId = reference("actor_resident_id", ResidentsTable.id, onDelete = ReferenceOption.RESTRICT)
    val note = varchar("note", 255)
    val targetMovementId = long("target_movement_id").references(id, onDelete = ReferenceOption.RESTRICT).nullable() // Correction のみ
    val reason = varchar("reason", 255).nullable() // Correction のみ

    init {
        index(false, productId, id)
    }

    const val KIND_REPLENISHMENT = "REPLENISHMENT"
    const val KIND_CONSUMPTION = "CONSUMPTION"
    const val KIND_CORRECTION = "CORRECTION"
}
