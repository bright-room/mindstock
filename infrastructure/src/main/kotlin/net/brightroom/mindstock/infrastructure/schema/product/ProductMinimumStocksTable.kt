package net.brightroom.mindstock.infrastructure.schema.product

import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.HistoryTable
import net.brightroom.mindstock.infrastructure.schema.user.UsersTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime

@Migratable
object ProductMinimumStocksTable : HistoryTable("product_minimum_stocks") {
    val product_id = reference("product_id", ProductsTable.id, onDelete = ReferenceOption.RESTRICT)
    val minimum_stock = integer("minimum_stock").check { it greaterEq 0 }
    val edited_by = reference("edited_by", UsersTable.id, onDelete = ReferenceOption.RESTRICT)
    val created_at = datetime("created_at").defaultExpression(CurrentDateTime)

    init {
        index(false, product_id, id)
    }
}
