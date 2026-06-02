package net.brightroom.mindstock.domain.model.inventory.stock.movement

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable
@JvmInline
value class OccurredAt(
    private val value: Instant,
) {
    operator fun invoke(): Instant = value

    override fun toString(): String = value.toString()

    companion object {
        fun now(): OccurredAt = OccurredAt(Clock.System.now())
    }
}
