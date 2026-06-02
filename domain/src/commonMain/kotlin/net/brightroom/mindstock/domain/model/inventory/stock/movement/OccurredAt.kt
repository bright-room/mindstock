package net.brightroom.mindstock.domain.model.inventory.stock.movement

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import net.brightroom.mindstock.extensions.kotlinx.datetime.now
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class OccurredAt(
    private val value: LocalDateTime,
) {
    operator fun invoke(): LocalDateTime = value

    override fun toString(): String = value.toString()

    companion object {
        fun now(): OccurredAt = OccurredAt(LocalDateTime.now())
    }
}
