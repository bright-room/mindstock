package net.brightroom.mindstock.domain.model.inventory.product.image

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/** クライアントがアップロードした原画像バイト列。application 公開 API で primitive ByteArray を晒さないための VO。 */
@Serializable
@JvmInline
value class RawImageUpload(
    private val value: ByteArray,
) {
    init {
        require(value.isNotEmpty()) { "RawImageUpload must not be empty" }
    }

    operator fun invoke(): ByteArray = value
}
