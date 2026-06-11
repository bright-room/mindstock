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

    // ByteArray は可変参照のため、内部配列を直接返さず防御的コピーを返して不変性を保つ。
    operator fun invoke(): ByteArray = value.copyOf()

    // ByteArray のデフォルト toString は `[B@hash` でログに無意味なため、サイズだけ可読化する。
    override fun toString(): String = "RawImageUpload(${value.size} bytes)"
}
