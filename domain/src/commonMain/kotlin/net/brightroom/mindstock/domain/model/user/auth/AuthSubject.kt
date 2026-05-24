package net.brightroom.mindstock.domain.model.user.auth

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.exception.DomainException
import kotlin.jvm.JvmInline

/**
 * 認証プロバイダにおけるサブジェクト識別子(OIDC の sub クレーム相当)。
 * 空文字は禁止。
 */
@Serializable
@JvmInline
value class AuthSubject(
    private val value: String,
) {
    init {
        if (value.isBlank()) throw DomainException.AuthSubjectBlank()
    }

    override fun toString(): String = value

    operator fun invoke(): String = value
}
