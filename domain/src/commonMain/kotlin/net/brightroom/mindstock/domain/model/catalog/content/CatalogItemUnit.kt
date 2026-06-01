package net.brightroom.mindstock.domain.model.catalog.content

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class CatalogItemUnit private constructor(
    private val value: String,
) {
    init {
        require(value.isNotEmpty() && value.length <= MAX_LENGTH && value == value.trim()) {
            "CatalogItemUnit must be 1..$MAX_LENGTH chars after trim"
        }
    }

    internal operator fun invoke(): String = value

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 10

        operator fun invoke(raw: String): CatalogItemUnit = CatalogItemUnit(raw.trim())
    }
}
