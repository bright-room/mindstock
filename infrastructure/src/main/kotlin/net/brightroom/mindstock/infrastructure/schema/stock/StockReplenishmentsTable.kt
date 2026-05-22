package net.brightroom.mindstock.infrastructure.schema.stock

import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.HistoryTable
import net.brightroom.mindstock.infrastructure.schema.product.ProductsTable
import net.brightroom.mindstock.infrastructure.schema.user.UsersTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.datetime

@Migratable
object StockReplenishmentsTable : HistoryTable("stock_replenishments") {
    val product_id = reference("product_id", ProductsTable.id, onDelete = ReferenceOption.RESTRICT)
    val quantity = integer("quantity").check { it greater 0 }
    val occurred_at = datetime("occurred_at")
    val acted_by = reference("acted_by", UsersTable.id, onDelete = ReferenceOption.RESTRICT)
    val note = text("note").nullable()

    init {
        index(false, product_id, id)
    }
}
