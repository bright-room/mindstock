package net.brightroom.mindstock.domain.model.household

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.support.requireTrimmedWithin
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class HouseholdName private constructor(
    private val value: String,
) {
    init {
        value.requireTrimmedWithin(MAX_LENGTH, "HouseholdName")
    }

    operator fun invoke(): String = value

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 30

        operator fun invoke(raw: String): HouseholdName = HouseholdName(raw.trim())
    }
}
