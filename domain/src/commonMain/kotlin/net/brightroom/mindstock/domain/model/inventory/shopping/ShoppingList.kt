package net.brightroom.mindstock.domain.model.inventory.shopping

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks

@Serializable
data class ShoppingList(
    val list: List<ShoppingEntry>,
) {
    fun size(): Int = list.size

    fun autoItems(): ShoppingList = ShoppingList(list.filter { it.need().is在庫不足() })

    fun manualItems(): ShoppingList = ShoppingList(list.filter { it.need().is手動希望() })

    companion object {
        /** Stock 集合と「手動希望の商品 ID 集合」から買い物リスト read-model を合成する。 */
        fun from(
            stocks: Stocks,
            wantedProductIds: Set<ProductId>,
        ): ShoppingList = ShoppingList(stocks.list.map { ShoppingEntry(it, Wanted(it.product.id in wantedProductIds)) })
    }
}
