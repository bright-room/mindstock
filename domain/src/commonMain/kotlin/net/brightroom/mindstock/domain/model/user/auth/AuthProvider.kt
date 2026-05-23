package net.brightroom.mindstock.domain.model.user.auth

import kotlinx.serialization.Serializable

/**
 * 認証プロバイダ識別子。
 * 将来的に AUTH0 等を追加可能だが、MVP は ZITADEL のみ。
 */
@Serializable
enum class AuthProvider {
    ZITADEL,
}
