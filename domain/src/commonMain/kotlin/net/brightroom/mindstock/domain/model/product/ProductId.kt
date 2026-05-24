package net.brightroom.mindstock.domain.model.product

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
@JvmInline
value class ProductId(
    private val value: Uuid,
) {
    override fun toString(): String = value.toString()

    operator fun invoke(): Uuid = value

    companion object {
        fun create(): ProductId = ProductId(Uuid.generateV7())
    }
}
