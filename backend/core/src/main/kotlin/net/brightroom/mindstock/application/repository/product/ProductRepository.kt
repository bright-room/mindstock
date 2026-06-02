package net.brightroom.mindstock.application.repository.product

import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.Products

interface ProductRepository {
    fun findById(id: ProductId): Product

    /** 採用中の商品一覧(空なら空 Products)。 */
    fun listByHousehold(householdId: HouseholdId): Products

    /** アーカイブ済の商品一覧。 */
    fun listArchivedByHousehold(householdId: HouseholdId): Products
}
