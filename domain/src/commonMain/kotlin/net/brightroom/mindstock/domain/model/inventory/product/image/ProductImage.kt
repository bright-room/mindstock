package net.brightroom.mindstock.domain.model.inventory.product.image

import kotlinx.serialization.Serializable

@Serializable
sealed interface ProductImage {
    @Serializable
    data object None : ProductImage

    @Serializable
    data class Stored(
        val ref: ImageRef,
    ) : ProductImage
}
