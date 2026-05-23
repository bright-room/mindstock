package net.brightroom.mindstock.domain.repository.product

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.Products

interface ProductRepository {
    /** 世帯の全商品(archived 含む)。 */
    fun listOf(household: Household): Products

    /** 同一世帯で同一カタログ商品を採用済みか引く(UNIQUE 検出用)。 */
    fun find(household: Household, catalogItem: CatalogItem): Product?
}
