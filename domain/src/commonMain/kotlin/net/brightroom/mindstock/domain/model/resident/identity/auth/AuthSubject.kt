package net.brightroom.mindstock.domain.model.resident.identity.auth

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class AuthSubject(
    private val value: String,
) {
    init {
        require(value.isNotBlank()) { "AuthSubject must not be blank" }
    }

    operator fun invoke(): String = value

    override fun toString(): String = value
}
