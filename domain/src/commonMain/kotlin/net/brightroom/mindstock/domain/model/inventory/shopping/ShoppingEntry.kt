package net.brightroom.mindstock.domain.model.inventory.shopping

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.inventory.stock.Stock

@Serializable
data class ShoppingEntry(
    val stock: Stock,
    val manuallyWanted: Wanted,
) {
    fun need(): ShoppingNeed = ShoppingNeed.judge(stock.status(), manuallyWanted)

    fun onList(): Boolean = need().onShoppingList
}
