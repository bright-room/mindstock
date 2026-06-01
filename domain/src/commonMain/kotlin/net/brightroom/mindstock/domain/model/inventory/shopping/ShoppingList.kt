package net.brightroom.mindstock.domain.model.inventory.shopping

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.inventory.stock.Stock

@Serializable
data class ShoppingEntry(
    val stock: Stock,
    val manuallyWanted: Boolean,
) {
    fun need(): ShoppingNeed = ShoppingNeed.judge(stock.status(), manuallyWanted)

    fun onList(): Boolean = need().onShoppingList
}

@Serializable
data class ShoppingList(
    val list: List<ShoppingEntry>,
) {
    fun size(): Int = list.size

    fun autoItems(): ShoppingList = ShoppingList(list.filter { it.need() == ShoppingNeed.在庫不足 })

    fun manualItems(): ShoppingList = ShoppingList(list.filter { it.need() == ShoppingNeed.手動希望 })
}
