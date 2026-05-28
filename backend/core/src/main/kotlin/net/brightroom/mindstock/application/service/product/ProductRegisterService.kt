package net.brightroom.mindstock.application.service.product

import net.brightroom.mindstock.application.repository.product.ProductRegisterRepository
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.user.User

class ProductRegisterService(
    private val productRegisterRepository: ProductRegisterRepository,
) {
    fun adopt(
        household: Household,
        catalogItem: CatalogItem,
    ): Product = productRegisterRepository.adopt(household, catalogItem)

    fun archive(
        product: Product,
        by: User,
    ) {
        productRegisterRepository.archive(product, by)
    }

    fun setMinimumStock(
        product: Product,
        value: MinimumStock,
        editedBy: User,
    ) {
        productRegisterRepository.setMinimumStock(product, value, editedBy)
    }
}
