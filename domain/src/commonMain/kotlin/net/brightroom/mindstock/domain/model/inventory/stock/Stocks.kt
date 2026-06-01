package net.brightroom.mindstock.domain.model.inventory.stock

import kotlinx.serialization.Serializable

@Serializable
data class Stocks(
    val list: List<Stock>,
) {
    fun size(): Int = list.size
}
