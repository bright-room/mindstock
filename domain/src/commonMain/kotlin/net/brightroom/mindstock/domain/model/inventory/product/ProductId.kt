@file:OptIn(ExperimentalUuidApi::class)

package net.brightroom.mindstock.domain.model.inventory.product

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
@JvmInline
value class ProductId(
    private val value: Uuid,
) {
    internal operator fun invoke(): Uuid = value

    override fun toString(): String = value.toString()

    companion object {
        fun create(): ProductId = ProductId(Uuid.generateV7())
    }
}
