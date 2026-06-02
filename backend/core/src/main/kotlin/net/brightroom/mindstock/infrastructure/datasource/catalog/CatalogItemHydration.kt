@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.catalog

import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.infrastructure.datasource.schemas.CatalogItemsTable
import org.jetbrains.exposed.v1.core.ResultRow

internal fun ResultRow.toCatalogItem(): CatalogItem =
    CatalogItem(
        id = CatalogItemId(this[CatalogItemsTable.id]),
        jan = Jan(this[CatalogItemsTable.jan]),
        name = CatalogItemName(this[CatalogItemsTable.name]),
    )
