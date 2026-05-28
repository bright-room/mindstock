package net.brightroom.mindstock.application.service.product

import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.Products

class ProductService(
    private val productRepository: ProductRepository,
) {
    fun find(
        household: Household,
        catalogItem: CatalogItem,
    ): Product? = productRepository.find(household, catalogItem)

    fun listOf(household: Household): Products = productRepository.listOf(household)
}
