package net.brightroom.mindstock.domain.model.catalog.content

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.support.requireTrimmedWithin
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class CatalogItemName private constructor(
    private val value: String,
) {
    init {
        value.requireTrimmedWithin(MAX_LENGTH, "CatalogItemName")
    }

    operator fun invoke(): String = value

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 60

        operator fun invoke(raw: String): CatalogItemName = CatalogItemName(raw.trim())
    }
}
