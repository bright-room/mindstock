package net.brightroom.mindstock.application.service.product

import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.product.Products

class ProductService(
    private val productRepository: ProductRepository,
) {
    suspend fun findById(id: ProductId): Product = productRepository.findById(id)

    suspend fun find(
        household: Household,
        catalogItem: CatalogItem,
    ): Product = productRepository.find(household, catalogItem)

    suspend fun listOf(household: Household): Products = productRepository.listOf(household)
}
