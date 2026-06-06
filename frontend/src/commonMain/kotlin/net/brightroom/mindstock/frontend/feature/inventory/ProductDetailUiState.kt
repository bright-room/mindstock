package net.brightroom.mindstock.frontend.feature.inventory

import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.frontend.core.ui.UiText

sealed interface ProductDetailUiState {
    data object Loading : ProductDetailUiState

    data class Content(
        val movements: StockMovements,
    ) : ProductDetailUiState

    data class Error(
        val text: UiText,
    ) : ProductDetailUiState
}
