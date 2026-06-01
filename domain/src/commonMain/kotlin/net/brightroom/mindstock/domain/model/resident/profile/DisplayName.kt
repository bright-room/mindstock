package net.brightroom.mindstock.domain.model.resident.profile

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class DisplayName private constructor(
    private val value: String,
) {
    internal operator fun invoke(): String = value

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 100

        operator fun invoke(raw: String): DisplayName {
            val trimmed = raw.trim()
            require(trimmed.isNotEmpty() && trimmed.length <= MAX_LENGTH) {
                "DisplayName must be 1..$MAX_LENGTH chars after trim"
            }
            return DisplayName(trimmed)
        }
    }
}
