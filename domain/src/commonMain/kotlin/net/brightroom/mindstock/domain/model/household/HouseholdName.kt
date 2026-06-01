package net.brightroom.mindstock.domain.model.household

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class HouseholdName private constructor(
    private val value: String,
) {
    internal operator fun invoke(): String = value

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 30

        operator fun invoke(raw: String): HouseholdName {
            val trimmed = raw.trim()
            require(trimmed.isNotEmpty() && trimmed.length <= MAX_LENGTH) {
                "HouseholdName must be 1..$MAX_LENGTH chars after trim"
            }
            return HouseholdName(trimmed)
        }
    }
}
