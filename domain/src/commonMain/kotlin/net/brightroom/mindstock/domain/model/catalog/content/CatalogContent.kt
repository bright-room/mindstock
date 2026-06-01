package net.brightroom.mindstock.domain.model.catalog.content

import kotlinx.serialization.Serializable

@Serializable
data class CatalogContent(
    val name: CatalogItemName,
    val defaultUnit: CatalogItemUnit,
)
