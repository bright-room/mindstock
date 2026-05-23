package net.brightroom.mindstock.infrastructure.schema.stock

import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.HistoryTable
import net.brightroom.mindstock.infrastructure.schema.user.UsersTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime

@Migratable
object StockReplenishmentCorrectionsTable : HistoryTable("stock_replenishment_corrections") {
    val stock_replenishment_id =
        reference(
            "stock_replenishment_id",
            StockReplenishmentsTable.id,
            onDelete = ReferenceOption.RESTRICT,
        )
    val corrected_quantity = integer("corrected_quantity").check { it greater 0 }
    val reason = text("reason").default("")
    val corrected_by =
        reference(
            "corrected_by",
            UsersTable.id,
            onDelete = ReferenceOption.RESTRICT,
        )
    val created_at = datetime("created_at").defaultExpression(CurrentDateTime)

    init {
        index(false, stock_replenishment_id, id)
    }
}
