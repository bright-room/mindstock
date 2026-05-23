package net.brightroom.mindstock.domain.model.catalog

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
@JvmInline
value class CatalogItemId(
    private val value: Uuid,
) {
    override fun toString(): String = value.toString()

    internal operator fun invoke(): Uuid = value

    companion object {
        fun create(): CatalogItemId = CatalogItemId(Uuid.generateV7())
    }
}
