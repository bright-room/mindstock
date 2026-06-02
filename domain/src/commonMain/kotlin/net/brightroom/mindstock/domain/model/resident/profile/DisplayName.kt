package net.brightroom.mindstock.domain.model.resident.profile

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class DisplayName private constructor(
    private val value: String,
) {
    init {
        require(value.isNotEmpty() && value.length <= MAX_LENGTH && value == value.trim()) {
            "DisplayName must be 1..$MAX_LENGTH chars after trim"
        }
    }

    operator fun invoke(): String = value

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 100

        operator fun invoke(raw: String): DisplayName = DisplayName(raw.trim())
    }
}
