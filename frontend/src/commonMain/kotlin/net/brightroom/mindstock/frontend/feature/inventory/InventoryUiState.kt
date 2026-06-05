package net.brightroom.mindstock.frontend.feature.inventory

import net.brightroom.mindstock.domain.model.inventory.stock.Stocks

sealed interface InventoryUiState {
    data object Loading : InventoryUiState

    data class Content(
        val stocks: Stocks,
        val view: StockView,
    ) : InventoryUiState

    data class Error(
        val message: String,
    ) : InventoryUiState
}
