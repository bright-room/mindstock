package net.brightroom.mindstock.domain.model.inventory.stock.movement

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class MovementId(
    private val value: Long,
) {
    init {
        require(value >= 0) { "MovementId must be >= 0: $value" }
    }

    internal operator fun invoke(): Long = value

    override fun toString(): String = value.toString()
}
