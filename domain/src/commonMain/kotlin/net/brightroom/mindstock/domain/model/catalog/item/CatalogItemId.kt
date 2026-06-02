@file:OptIn(ExperimentalUuidApi::class)

package net.brightroom.mindstock.domain.model.catalog.item

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
@JvmInline
value class CatalogItemId(
    private val value: Uuid,
) {
    operator fun invoke(): Uuid = value

    override fun toString(): String = value.toString()

    companion object {
        fun create(): CatalogItemId = CatalogItemId(Uuid.generateV7())
    }
}
