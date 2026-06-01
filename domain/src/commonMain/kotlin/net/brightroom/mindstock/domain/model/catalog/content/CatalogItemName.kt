package net.brightroom.mindstock.domain.model.catalog.content

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class CatalogItemName private constructor(
    private val value: String,
) {
    init {
        require(value.isNotEmpty() && value.length <= MAX_LENGTH && value == value.trim()) {
            "CatalogItemName must be 1..$MAX_LENGTH chars after trim"
        }
    }

    internal operator fun invoke(): String = value

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 60

        operator fun invoke(raw: String): CatalogItemName = CatalogItemName(raw.trim())
    }
}
