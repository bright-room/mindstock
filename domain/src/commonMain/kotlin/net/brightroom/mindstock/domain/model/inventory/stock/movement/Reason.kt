package net.brightroom.mindstock.domain.model.inventory.stock.movement

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.support.requireTrimmedWithin
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class Reason private constructor(
    private val value: String,
) {
    init {
        value.requireTrimmedWithin(MAX_LENGTH, "Reason")
    }

    operator fun invoke(): String = value

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 255

        operator fun invoke(raw: String): Reason = Reason(raw.trim())
    }
}
