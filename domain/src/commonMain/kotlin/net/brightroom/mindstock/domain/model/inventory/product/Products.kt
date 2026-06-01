package net.brightroom.mindstock.domain.model.inventory.product

import kotlinx.serialization.Serializable

@Serializable
data class Products(
    val list: List<Product>,
) {
    fun size(): Int = list.size
}
