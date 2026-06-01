package net.brightroom.mindstock.domain.model.catalog.item

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.catalog.barcode.Barcode
import net.brightroom.mindstock.domain.model.catalog.content.CatalogContent
import net.brightroom.mindstock.domain.model.catalog.origin.CatalogOrigin

@Serializable
data class CatalogItem(
    val id: CatalogItemId,
    val content: CatalogContent,
    val barcode: Barcode,
    val origin: CatalogOrigin,
)
