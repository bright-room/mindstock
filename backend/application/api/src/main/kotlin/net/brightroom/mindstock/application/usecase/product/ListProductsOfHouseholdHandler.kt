package net.brightroom.mindstock.application.usecase.product

import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.Products

class ListProductsOfHouseholdHandler(
    private val productRepository: ProductRepository,
) {
    fun handle(household: Household): Products = productRepository.listOf(household)
}
