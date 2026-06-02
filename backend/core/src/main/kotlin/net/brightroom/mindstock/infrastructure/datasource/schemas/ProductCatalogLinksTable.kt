@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object ProductCatalogLinksTable : Table("product_catalog_links") {
    val productId = reference("product_id", ProductsTable.id, onDelete = ReferenceOption.RESTRICT)
    val catalogItemId = reference("catalog_item_id", CatalogItemsTable.id, onDelete = ReferenceOption.RESTRICT)
    override val primaryKey = PrimaryKey(productId)

    init {
        index(false, catalogItemId)
    }
}
