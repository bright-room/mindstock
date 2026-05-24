package net.brightroom.mindstock.application.usecase.product

import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.repository.product.ProductRegisterRepository

class ArchiveProductHandler(
    private val productRegisterRepository: ProductRegisterRepository,
) {
    fun handle(
        product: Product,
        by: User,
    ) {
        productRegisterRepository.archive(product, by)
    }
}
