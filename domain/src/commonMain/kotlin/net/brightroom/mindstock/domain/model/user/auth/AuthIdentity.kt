package net.brightroom.mindstock.domain.model.user.auth

import kotlinx.serialization.Serializable

/**
 * 認証プロバイダの識別情報。User が外部認証(Zitadel 等)と紐付くキー。
 * provider + subject の組で一意。
 */
@Serializable
data class AuthIdentity(
    val provider: AuthProvider,
    val subject: AuthSubject,
)
