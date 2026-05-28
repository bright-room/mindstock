package net.brightroom.mindstock.configuration.auth

import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity

/**
 * 認証済み呼び出し元の Principal。
 * JWT 検証成功時に AuthenticationProvider が生成する。
 * UserId は持たず、必要なら ActorResolver が UserRepository.findByAuthIdentity で解決する。
 *
 * Ktor 3.x では `Principal` marker interface が deprecated のため、プレーンな data class として定義する。
 */
data class MindstockPrincipal(
    val authIdentity: AuthIdentity,
)
