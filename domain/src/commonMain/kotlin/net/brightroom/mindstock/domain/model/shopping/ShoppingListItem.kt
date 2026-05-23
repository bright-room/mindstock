package net.brightroom.mindstock.domain.model.shopping

import net.brightroom.mindstock.domain.model.stock.Stock

/**
 * 買い物リスト 1 行。
 */
data class ShoppingListItem(val stock: Stock, val shortage: Int)
