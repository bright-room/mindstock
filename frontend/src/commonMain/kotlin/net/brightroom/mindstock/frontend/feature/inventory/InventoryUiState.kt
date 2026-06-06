package net.brightroom.mindstock.frontend.feature.inventory

import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.frontend.core.ui.UiText

sealed interface InventoryUiState {
    data object Loading : InventoryUiState

    data class Content(
        val stocks: Stocks,
        val view: StockView,
    ) : InventoryUiState

    data class Error(
        val text: UiText,
    ) : InventoryUiState
}
