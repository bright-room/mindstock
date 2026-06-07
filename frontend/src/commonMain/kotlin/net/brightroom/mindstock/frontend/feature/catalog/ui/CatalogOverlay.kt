package net.brightroom.mindstock.frontend.feature.catalog.ui

import net.brightroom.mindstock.domain.model.inventory.stock.Stock

/** app 層が重ねる商品系 overlay の種別。 */
sealed interface CatalogOverlay {
    data object AddProduct : CatalogOverlay

    data object Master : CatalogOverlay

    data object Archived : CatalogOverlay

    data class Settings(
        val stock: Stock,
    ) : CatalogOverlay
}
