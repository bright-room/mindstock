package net.brightroom.mindstock.domain.model.inventory.stock.movement

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class Note(
    private val value: String,
) {
    init {
        require(value.trim().length <= MAX_LENGTH) {
            "Note must be at most $MAX_LENGTH chars after trim: '$value'"
        }
    }

    internal operator fun invoke(): String = value.trim()

    override fun toString(): String = value.trim()

    companion object {
        const val MAX_LENGTH = 255
    }
}
