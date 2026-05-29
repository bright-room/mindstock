package net.brightroom.mindstock.infrastructure.datasource.product

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.ProductId
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal fun hydrateProduct(
    id: Uuid,
    catalogItem: CatalogItem,
    minimumStock: Int?,
    archived: Boolean,
): Product =
    Product(
        id = ProductId(id),
        catalogItem = catalogItem,
        minimumStock = if (minimumStock != null) MinimumStock.Set(minimumStock) else MinimumStock.NotSet,
        archived = archived,
    )
