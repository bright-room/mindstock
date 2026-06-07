package net.brightroom.mindstock.frontend.feature.catalog

import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItems

/** 商品追加フローの段階。 */
sealed interface AddProductUiState {
    data class Browsing(
        val results: CatalogItems = CatalogItems(emptyList()),
        val phase: BrowsePhase = BrowsePhase.Idle,
    ) : AddProductUiState

    data class AdoptForm(
        val item: CatalogItem,
    ) : AddProductUiState

    data class CustomForm(
        val seedName: String,
        val jan: Jan?,
        val nameLocked: Boolean,
    ) : AddProductUiState

    data object Done : AddProductUiState
}

enum class BrowsePhase { Idle, Searching, JanLookingUp }
