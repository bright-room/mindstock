package net.brightroom.mindstock.domain.model.household

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class HouseholdName private constructor(
    private val value: String,
) {
    init {
        require(value.isNotEmpty() && value.length <= MAX_LENGTH && value == value.trim()) {
            "HouseholdName must be 1..$MAX_LENGTH chars after trim"
        }
    }

    operator fun invoke(): String = value

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 30

        operator fun invoke(raw: String): HouseholdName = HouseholdName(raw.trim())
    }
}
