package net.brightroom.mindstock.domain.model.catalog

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.exception.DomainException
import kotlin.jvm.JvmInline

/**
 * カタログ商品の名前。空文字禁止、最大 200 文字。
 */
@Serializable
@JvmInline
value class CatalogItemName(
    private val value: String,
) {
    init {
        if (value.isBlank()) throw DomainException.CatalogItemNameBlank()
        if (value.length > 200) throw DomainException.CatalogItemNameTooLong(value.length)
    }

    override fun toString(): String = value

    internal operator fun invoke(): String = value
}
