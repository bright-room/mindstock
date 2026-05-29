package net.brightroom.mindstock.application.repository.product

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.user.User

interface ProductRegisterRepository {
    /** products に行を INSERT。 */
    fun adopt(
        household: Household,
        catalogItem: CatalogItem,
    ): Product

    /** product_minimum_stocks に行を INSERT。 */
    fun setMinimumStock(
        product: Product,
        value: MinimumStock,
        editedBy: User,
    )

    /** product_archives に行を INSERT。 */
    fun archive(
        product: Product,
        by: User,
    )
}
