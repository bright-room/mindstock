package net.brightroom.mindstock.frontend.feature.inventory

import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.frontend.core.ui.UiText

sealed interface InventoryUiState {
    data object Loading : InventoryUiState

    data class Content(
        val stocks: Stocks,
        val view: StockView,
        val query: String = "",
        // status=十分 かつ手動希望の商品 ID(在庫一覧の「リスト」バッジ / need 件数の want 加算に使う)。
        val wantedProductIds: Set<ProductId> = emptySet(),
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
