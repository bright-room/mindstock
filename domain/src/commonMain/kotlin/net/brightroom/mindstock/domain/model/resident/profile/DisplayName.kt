package net.brightroom.mindstock.domain.model.resident.profile

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.support.requireTrimmedWithin
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class DisplayName private constructor(
    private val value: String,
) {
    init {
        value.requireTrimmedWithin(MAX_LENGTH, "DisplayName")
    }

    operator fun invoke(): String = value

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 100

        operator fun invoke(raw: String): DisplayName = DisplayName(raw.trim())
    }
}
