package net.brightroom.mindstock.domain.model.inventory.shopping

import kotlinx.serialization.Serializable

@Serializable
data class ShoppingList(
    val list: List<ShoppingEntry>,
) {
    fun size(): Int = list.size

    fun autoItems(): ShoppingList = ShoppingList(list.filter { it.need().is在庫不足() })

    fun manualItems(): ShoppingList = ShoppingList(list.filter { it.need().is手動希望() })
}
