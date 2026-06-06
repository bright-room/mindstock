package net.brightroom.mindstock.frontend.feature.catalog

import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.frontend.core.ui.UiText

sealed interface ProductMasterUiState {
    data object Loading : ProductMasterUiState

    data class Content(
        val stocks: Stocks,
    ) : ProductMasterUiState

    data class Error(
        val text: UiText,
    ) : ProductMasterUiState
}
