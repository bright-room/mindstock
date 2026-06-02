package net.brightroom.mindstock.domain.model.inventory.stock.movement

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class Note private constructor(
    private val value: String,
) {
    init {
        require(value.length <= MAX_LENGTH && value == value.trim()) {
            "Note must be at most 255 chars after trim"
        }
    }

    operator fun invoke(): String = value

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 255

        operator fun invoke(raw: String): Note = Note(raw.trim())
    }
}
