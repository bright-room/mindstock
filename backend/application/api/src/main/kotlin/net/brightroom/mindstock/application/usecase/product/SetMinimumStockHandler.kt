package net.brightroom.mindstock.application.usecase.product

import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.repository.product.ProductRegisterRepository

class SetMinimumStockHandler(
    private val productRegisterRepository: ProductRegisterRepository,
) {
    fun handle(
        product: Product,
        value: MinimumStock,
        editedBy: User,
    ) {
        productRegisterRepository.setMinimumStock(product, value, editedBy)
    }
}
