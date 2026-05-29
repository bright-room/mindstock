package net.brightroom.mindstock.rpc

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.product.Products

@Rpc
interface ProductRpcService {
    suspend fun listOfHousehold(householdId: HouseholdId): Products

    suspend fun find(
        householdId: HouseholdId,
        catalogItemId: CatalogItemId,
    ): Product?

    suspend fun adopt(
        householdId: HouseholdId,
        catalogItemId: CatalogItemId,
    ): Product

    suspend fun setMinimumStock(
        id: ProductId,
        minimumStock: MinimumStock,
    )

    suspend fun archive(id: ProductId)
}
