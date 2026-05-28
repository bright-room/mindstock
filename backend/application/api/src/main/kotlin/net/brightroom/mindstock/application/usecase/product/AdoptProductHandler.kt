package net.brightroom.mindstock.application.usecase.product

import net.brightroom.mindstock.application.repository.product.ProductRegisterRepository
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.Product

class AdoptProductHandler(
    private val productRegisterRepository: ProductRegisterRepository,
) {
    fun handle(
        household: Household,
        catalogItem: CatalogItem,
    ): Product = productRegisterRepository.adopt(household, catalogItem)
}
