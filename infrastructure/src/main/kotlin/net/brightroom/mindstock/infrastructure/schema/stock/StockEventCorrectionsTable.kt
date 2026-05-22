package net.brightroom.mindstock.infrastructure.schema.stock

import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.HistoryTable
import net.brightroom.mindstock.infrastructure.schema.user.UsersTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.or

@Migratable
object StockEventCorrectionsTable : HistoryTable("stock_event_corrections") {
    val target_table = text("target_table").check {
        (it eq "stock_replenishments") or (it eq "stock_consumptions")
    }
    val target_id = long("target_id")
    val new_quantity = integer("new_quantity").check { it greater 0 }
    val reason = text("reason").nullable()
    val corrected_by = reference("corrected_by", UsersTable.id, onDelete = ReferenceOption.RESTRICT)

    init {
        index(false, target_table, target_id, id)
    }
}
