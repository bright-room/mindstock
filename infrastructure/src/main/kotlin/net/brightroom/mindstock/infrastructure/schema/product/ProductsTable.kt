package net.brightroom.mindstock.infrastructure.schema.product

import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.AggregateRootTable
import net.brightroom.mindstock.infrastructure.schema.catalog.CatalogItemsTable
import net.brightroom.mindstock.infrastructure.schema.household.HouseholdsTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime

@Migratable
object ProductsTable : AggregateRootTable("products") {
    val household_id = reference("household_id", HouseholdsTable.id, onDelete = ReferenceOption.RESTRICT)
    val catalog_item_id = reference("catalog_item_id", CatalogItemsTable.id, onDelete = ReferenceOption.RESTRICT)
    val created_at = datetime("created_at").defaultExpression(CurrentDateTime)

    init {
        uniqueIndex("uq_products_household_catalog", household_id, catalog_item_id)
    }
}
