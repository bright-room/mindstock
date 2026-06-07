package net.brightroom.mindstock.frontend.feature.catalog

import net.brightroom.mindstock.domain.model.inventory.product.Products
import net.brightroom.mindstock.frontend.core.ui.UiText

sealed interface ArchivedUiState {
    data object Loading : ArchivedUiState

    data class Content(
        val products: Products,
    ) : ArchivedUiState

    data class Error(
        val text: UiText,
    ) : ArchivedUiState
}
