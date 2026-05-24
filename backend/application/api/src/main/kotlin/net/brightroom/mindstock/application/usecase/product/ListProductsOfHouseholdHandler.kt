package net.brightroom.mindstock.application.usecase.product

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.Products
import net.brightroom.mindstock.domain.repository.product.ProductRepository

class ListProductsOfHouseholdHandler(
    private val productRepository: ProductRepository,
) {
    fun handle(household: Household): Products = productRepository.listOf(household)
}
