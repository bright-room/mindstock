package net.brightroom.mindstock.infrastructure.schemas.stock

import net.brightroom.mindstock.infrastructure.migration.annotation.Migratable
import net.brightroom.mindstock.infrastructure.schemas.HistoryTable
import net.brightroom.mindstock.infrastructure.schemas.product.ProductsTable
import net.brightroom.mindstock.infrastructure.schemas.user.UsersTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

@Migratable
object StockConsumptionsTable : HistoryTable("stock_consumptions") {
    val product_id = reference("product_id", ProductsTable.id, onDelete = ReferenceOption.RESTRICT)
    val quantity = integer("quantity").check { it greater 0 }
    val occurred_at = timestampWithTimeZone("occurred_at")
    val acted_by = reference("acted_by", UsersTable.id, onDelete = ReferenceOption.RESTRICT)
    val note = text("note").default("")
    val created_at = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    init {
        index(false, product_id, id)
    }
}
