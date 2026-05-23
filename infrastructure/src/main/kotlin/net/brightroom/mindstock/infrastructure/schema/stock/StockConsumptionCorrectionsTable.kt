package net.brightroom.mindstock.infrastructure.schema.stock

import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.HistoryTable
import net.brightroom.mindstock.infrastructure.schema.user.UsersTable
import org.jetbrains.exposed.v1.core.ReferenceOption

@Migratable
object StockConsumptionCorrectionsTable : HistoryTable("stock_consumption_corrections") {
    val target_id =
        reference(
            "target_id",
            StockConsumptionsTable.id,
            onDelete = ReferenceOption.RESTRICT,
        )
    val new_quantity = integer("new_quantity").check { it greater 0 }
    val reason = text("reason")
    val corrected_by =
        reference(
            "corrected_by",
            UsersTable.id,
            onDelete = ReferenceOption.RESTRICT,
        )

    init {
        index(false, target_id, id)
    }
}
