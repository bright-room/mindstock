package net.brightroom.mindstock.domain.model.shopping

import net.brightroom.mindstock.domain.model.stock.Stock

/**
 * 買い物リストというドメイン概念。
 *
 * Stock のリストから「閾値以下の商品」を抽出する。
 */
class ShoppingList(
    private val stocks: List<Stock>,
) {
    fun itemsToBuy(): List<ShoppingListItem> =
        stocks
            .filter { it.needsReplenishment() }
            .map { ShoppingListItem(it, shortage = it.shortage()) }
}
