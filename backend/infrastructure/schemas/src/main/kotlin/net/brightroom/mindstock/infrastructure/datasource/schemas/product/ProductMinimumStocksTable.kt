@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas.product

import net.brightroom.mindstock.infrastructure.datasource.schemas.HistoryTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.user.UsersTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object ProductMinimumStocksTable : HistoryTable("product_minimum_stocks") {
    val product_id = reference("product_id", ProductsTable.id, onDelete = ReferenceOption.RESTRICT)
    val minimum_stock = integer("minimum_stock").check { it greaterEq 0 }
    val edited_by = reference("edited_by", UsersTable.id, onDelete = ReferenceOption.RESTRICT)
    val created_at = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    init {
        index(false, product_id, id)
    }
}
