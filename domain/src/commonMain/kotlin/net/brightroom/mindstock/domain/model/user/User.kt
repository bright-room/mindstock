package net.brightroom.mindstock.domain.model.user

import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity

/**
 * アプリ内ユーザー集約。
 * 認証プロバイダの AuthIdentity と表示名を保持する。
 */
data class User(
    val id: UserId,
    val authIdentity: AuthIdentity,
    val displayName: DisplayName,
)
