package net.brightroom.mindstock.domain.model.product

import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

@OptIn(ExperimentalUuidApi::class)
@Serializable
@JvmInline
value class ProductId(
    private val value: Uuid,
) {
    override fun toString(): String = value.toString()

    internal operator fun invoke(): Uuid = value

    companion object {
        fun create(): ProductId = ProductId(Uuid.generateV7())
    }
}
