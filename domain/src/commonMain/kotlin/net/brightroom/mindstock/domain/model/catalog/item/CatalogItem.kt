package net.brightroom.mindstock.domain.model.catalog.item

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemName

@Serializable
data class CatalogItem(
    val id: CatalogItemId,
    val jan: Jan,
    val name: CatalogItemName,
)
