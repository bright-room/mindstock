package net.brightroom.mindstock.application.usecase.product

import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.Product

class FindProductHandler(
    private val productRepository: ProductRepository,
) {
    fun handle(
        household: Household,
        catalogItem: CatalogItem,
    ): Product? = productRepository.find(household, catalogItem)
}
