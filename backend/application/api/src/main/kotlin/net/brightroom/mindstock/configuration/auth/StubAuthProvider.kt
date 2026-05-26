package net.brightroom.mindstock.configuration.auth

import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject

/**
 * 開発・テスト専用の認証スタブ(一時的)。Task 7 で削除する。
 * Bearer token をそのまま AuthSubject として解釈する。
 */
object StubAuthProvider {
    fun resolve(token: String): MindstockPrincipal = MindstockPrincipal(AuthIdentity(AuthProvider.ZITADEL, AuthSubject(token)))
}
