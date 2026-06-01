package net.brightroom.mindstock.domain.model.inventory.product.image

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class ImageRef(
    private val value: String,
) {
    init {
        require(value.isNotBlank()) { "ImageRef must not be blank" }
    }

    internal operator fun invoke(): String = value

    override fun toString(): String = value
}
