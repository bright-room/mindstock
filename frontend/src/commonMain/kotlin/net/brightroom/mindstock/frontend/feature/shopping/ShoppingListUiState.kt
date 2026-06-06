package net.brightroom.mindstock.frontend.feature.shopping

import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingList
import net.brightroom.mindstock.frontend.core.ui.UiText

sealed interface ShoppingListUiState {
    data object Loading : ShoppingListUiState

    data class Content(
        val shoppingList: ShoppingList,
    ) : ShoppingListUiState {
        /** 在庫不足の自動アイテム。 */
        fun auto(): ShoppingList = shoppingList.autoItems()

        /** 手動希望のアイテム。 */
        fun manual(): ShoppingList = shoppingList.manualItems()

        /** 「在庫から探して追加」候補（まだリストに載っていない採用済み）。 */
        fun addable(): ShoppingList = ShoppingList(shoppingList.list.filter { !it.onList() })
    }

    data class Error(
        val text: UiText,
    ) : ShoppingListUiState
}
