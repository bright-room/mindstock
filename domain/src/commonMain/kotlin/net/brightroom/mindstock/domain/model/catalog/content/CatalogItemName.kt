package net.brightroom.mindstock.domain.model.catalog.content

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class CatalogItemName(
    private val value: String,
) {
    init {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= MAX_LENGTH) {
            "CatalogItemName must be 1..$MAX_LENGTH chars after trim: '$value'"
        }
    }

    internal operator fun invoke(): String = value.trim()

    override fun toString(): String = value.trim()

    companion object {
        const val MAX_LENGTH = 60
    }
}
