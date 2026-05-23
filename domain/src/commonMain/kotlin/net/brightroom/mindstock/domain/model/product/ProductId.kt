package net.brightroom.mindstock.domain.model.product

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
@JvmInline
public value class ProductId(private val value: Uuid) {
    override fun toString(): String = value.toString()

    internal operator fun invoke(): Uuid = value
}
