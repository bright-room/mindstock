package net.brightroom.mindstock.infrastructure.datasource.repository.product

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
        minimumStock = minimumStock?.let { MinimumStock(it) },
        archived = archived,
    )
