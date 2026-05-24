package net.brightroom.mindstock.domain.model.catalog

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.exception.DomainException
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
        if (value.isBlank()) throw DomainException.CatalogItemUnitBlank()
        if (value.length > 10) throw DomainException.CatalogItemUnitTooLong(value.length)
    }

    override fun toString(): String = value

    operator fun invoke(): String = value
}
