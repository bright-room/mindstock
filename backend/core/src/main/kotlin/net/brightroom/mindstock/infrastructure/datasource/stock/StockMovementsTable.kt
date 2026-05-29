@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.stock

import net.brightroom.mindstock.infrastructure.datasource.HistoryTable
import net.brightroom.mindstock.infrastructure.datasource.product.ProductsTable
import net.brightroom.mindstock.infrastructure.datasource.user.UsersTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object StockMovementsTable : HistoryTable("stock_movements") {
    val product_id = reference("product_id", ProductsTable.id, onDelete = ReferenceOption.RESTRICT)
    val type = enumerationByName<StockMovementType>("type", 20)
    val quantity = integer("quantity").check { it greater 0 }
    val occurred_at = timestampWithTimeZone("occurred_at")
    val acted_by = reference("acted_by", UsersTable.id, onDelete = ReferenceOption.RESTRICT)
    val note = text("note").default("")
    val created_at = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    init {
        index(false, product_id, id)
    }
}
