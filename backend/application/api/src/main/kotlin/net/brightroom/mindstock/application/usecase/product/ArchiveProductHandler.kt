package net.brightroom.mindstock.application.usecase.product

import net.brightroom.mindstock.application.repository.product.ProductRegisterRepository
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.user.User

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
