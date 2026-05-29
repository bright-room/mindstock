package net.brightroom.mindstock.domain.model.shopping

import net.brightroom.mindstock.domain.model.stock.Stocks

/**
 * 買い物リストというドメイン概念。
 *
 * Stock のリストから「閾値以下の商品」を抽出する。
 */
class ShoppingList(
    private val stocks: Stocks,
) {
    fun itemsToBuy(): List<ShoppingListItem> =
        stocks
            .needsReplenishment()
            .map { ShoppingListItem(it, shortage = it.shortage()) }
}
