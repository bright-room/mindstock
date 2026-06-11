package net.brightroom.mindstock.domain.model.inventory.product.image

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/** presigned GET URL。imageUrl RPC の戻り値 VO。 */
@Serializable
@JvmInline
value class ImageUrl(
    private val value: String,
) {
    init {
        require(value.isNotBlank()) { "ImageUrl must not be blank" }
    }

    operator fun invoke(): String = value

    override fun toString(): String = value
}
