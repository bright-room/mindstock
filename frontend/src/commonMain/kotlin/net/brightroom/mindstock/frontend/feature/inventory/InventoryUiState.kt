package net.brightroom.mindstock.frontend.feature.inventory

import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.frontend.core.ui.UiText

sealed interface InventoryUiState {
    data object Loading : InventoryUiState

    data class Content(
        val stocks: Stocks,
        val view: StockView,
        val query: String = "",
    ) : InventoryUiState {
        /** query で名前 substring 絞り込み（frontend 側フィルタ）。 */
        fun visibleStocks(): Stocks {
            val q = query.trim()
            if (q.isEmpty()) return stocks
            return Stocks(stocks.list.filter { it.product.name().contains(q, ignoreCase = true) })
        }
    }

    data class Error(
        val text: UiText,
    ) : InventoryUiState
}
