package net.brightroom.mindstock.infrastructure.schemas.stock

import net.brightroom.mindstock.infrastructure.migration.annotation.Migratable
import net.brightroom.mindstock.infrastructure.schemas.HistoryTable
import net.brightroom.mindstock.infrastructure.schemas.user.UsersTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

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
    val created_at = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    init {
        index(false, stock_replenishment_id, id)
    }
}
