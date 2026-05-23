package net.brightroom.mindstock.infrastructure.schema.stock

import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.HistoryTable
import net.brightroom.mindstock.infrastructure.schema.user.UsersTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime

@Migratable
object StockConsumptionCorrectionsTable : HistoryTable("stock_consumption_corrections") {
    val stock_consumption_id =
        reference(
            "stock_consumption_id",
            StockConsumptionsTable.id,
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
        index(false, stock_consumption_id, id)
    }
}
