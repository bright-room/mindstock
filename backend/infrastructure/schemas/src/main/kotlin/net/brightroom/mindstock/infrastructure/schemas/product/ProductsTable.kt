package net.brightroom.mindstock.infrastructure.schemas.product

import net.brightroom.mindstock.infrastructure.migration.annotation.Migratable
import net.brightroom.mindstock.infrastructure.schemas.AggregateRootTable
import net.brightroom.mindstock.infrastructure.schemas.catalog.CatalogItemsTable
import net.brightroom.mindstock.infrastructure.schemas.household.HouseholdsTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

@Migratable
object ProductsTable : AggregateRootTable("products") {
    val household_id = reference("household_id", HouseholdsTable.id, onDelete = ReferenceOption.RESTRICT)
    val catalog_item_id = reference("catalog_item_id", CatalogItemsTable.id, onDelete = ReferenceOption.RESTRICT)
    val created_at = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    init {
        uniqueIndex("uq_products_household_catalog", household_id, catalog_item_id)
    }
}
