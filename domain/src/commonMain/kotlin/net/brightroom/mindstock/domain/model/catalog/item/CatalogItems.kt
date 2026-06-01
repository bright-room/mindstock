package net.brightroom.mindstock.domain.model.catalog.item

import kotlinx.serialization.Serializable

@Serializable
data class CatalogItems(
    val list: List<CatalogItem>,
) {
    fun size(): Int = list.size
}
