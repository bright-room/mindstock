package net.brightroom.mindstock.domain.model.resident.profile

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class DisplayName(
    private val value: String,
) {
    init {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= MAX_LENGTH) {
            "DisplayName must be 1..$MAX_LENGTH chars after trim: '$value'"
        }
    }

    internal operator fun invoke(): String = value.trim()

    override fun toString(): String = value.trim()

    companion object {
        const val MAX_LENGTH = 100
    }
}
