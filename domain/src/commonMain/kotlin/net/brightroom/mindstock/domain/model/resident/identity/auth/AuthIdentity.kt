package net.brightroom.mindstock.domain.model.resident.identity.auth

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
enum class AuthProvider { ZITADEL, }

@Serializable
@JvmInline
value class AuthSubject(
    private val value: String,
) {
    init {
        require(value.isNotBlank()) { "AuthSubject must not be blank" }
    }

    internal operator fun invoke(): String = value

    override fun toString(): String = value
}

@Serializable
data class AuthIdentity(
    val provider: AuthProvider,
    val subject: AuthSubject,
)
