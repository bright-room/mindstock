package net.brightroom.mindstock.domain.model.inventory.quantity

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class Quantity(
    private val value: Int,
) {
    init {
        require(value > 0) { "Quantity must be positive: $value" }
    }

    internal operator fun invoke(): Int = value

    override fun toString(): String = value.toString()
}
