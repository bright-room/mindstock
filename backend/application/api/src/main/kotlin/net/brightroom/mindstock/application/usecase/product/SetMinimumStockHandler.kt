package net.brightroom.mindstock.application.usecase.product

import net.brightroom.mindstock.application.repository.product.ProductRegisterRepository
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.user.User

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
