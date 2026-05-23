package net.brightroom.mindstock.domain.repository.product

import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.user.UserId

public interface ProductRegisterRepository {
    public fun adopt(id: ProductId, householdId: HouseholdId, catalogItemId: CatalogItemId)
    public fun setMinimumStock(productId: ProductId, value: MinimumStock, editedBy: UserId)
    public fun archive(productId: ProductId, archivedBy: UserId)
}
