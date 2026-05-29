@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.product

import net.brightroom.mindstock.infrastructure.datasource.AggregateRootTable
import net.brightroom.mindstock.infrastructure.datasource.catalog.CatalogItemsTable
import net.brightroom.mindstock.infrastructure.datasource.household.HouseholdsTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object ProductsTable : AggregateRootTable("products") {
    val household_id = reference("household_id", HouseholdsTable.id, onDelete = ReferenceOption.RESTRICT)
    val catalog_item_id = reference("catalog_item_id", CatalogItemsTable.id, onDelete = ReferenceOption.RESTRICT)
    val created_at = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    init {
        uniqueIndex("uq_products_household_catalog", household_id, catalog_item_id)
    }
}
