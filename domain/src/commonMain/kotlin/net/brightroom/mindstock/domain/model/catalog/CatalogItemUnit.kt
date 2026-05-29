package net.brightroom.mindstock.domain.model.catalog

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * カタログ商品の単位。空文字禁止、最大 10 文字。
 */
@Serializable
@JvmInline
value class CatalogItemUnit(
    private val value: String,
) {
    init {
        require(value.isNotBlank()) { "catalog item unit must not be blank" }
        require(value.length <= 10) { "catalog item unit length ${value.length} > 10" }
    }

    override fun toString(): String = value

    operator fun invoke(): String = value
}
