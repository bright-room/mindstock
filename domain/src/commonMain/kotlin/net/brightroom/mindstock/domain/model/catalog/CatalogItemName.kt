package net.brightroom.mindstock.domain.model.catalog

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * カタログ商品の名前。空白のみ(空文字含む)禁止、最大 200 文字。
 */
@Serializable
@JvmInline
value class CatalogItemName(
    private val value: String,
) {
    init {
        require(value.isNotBlank()) { "catalog item name must not be blank" }
        require(value.length <= 200) { "catalog item name length ${value.length} > 200" }
    }

    override fun toString(): String = value

    operator fun invoke(): String = value
}
