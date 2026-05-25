package net.brightroom.mindstock.configuration.auth

import net.brightroom.mindstock.domain.model.user.UserId

/**
 * 認証済み呼び出し元の Principal。
 * Ktor 3.x では `Principal` marker interface が deprecated のため、プレーンな data class として定義する。
 */
data class MindstockPrincipal(
    val userId: UserId,
)
