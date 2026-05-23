package net.brightroom.mindstock.domain.repository.product

import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.ProductId

public interface ProductRepository {
    /** products + 最新 minimum_stock + 最新 archive を joins。 */
    public fun findById(id: ProductId): Product?

    /** 同一世帯で同一カタログ商品を採用済みか引く(`UNIQUE` 検出用)。 */
    public fun findByHouseholdAndCatalog(
        householdId: HouseholdId,
        catalogItemId: CatalogItemId,
    ): Product?

    /** 世帯のすべての商品(アーカイブ含む。フィルタは Application で)。 */
    public fun listByHousehold(householdId: HouseholdId): List<Product>
}
