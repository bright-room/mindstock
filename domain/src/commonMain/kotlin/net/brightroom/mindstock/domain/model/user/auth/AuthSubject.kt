package net.brightroom.mindstock.domain.model.user.auth

import kotlinx.serialization.Serializable
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
        require(value.isNotBlank()) { "auth subject must not be blank" }
    }

    override fun toString(): String = value

    operator fun invoke(): String = value
}
