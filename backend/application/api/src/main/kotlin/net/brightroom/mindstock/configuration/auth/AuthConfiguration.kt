package net.brightroom.mindstock.configuration.auth

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer

/**
 * Ktor Authentication プラグインを install し、Bearer token を [StubAuthProvider] で検証する。
 * 本格的な認証 (JWT 検証等) は別 Plan で置き換える。
 *
 * TODO(real-auth): 本番デプロイ前に [StubAuthProvider] を IdP 連携の実装に差し替える。
 *   現状は本番/開発環境を区別せず常に Stub が有効化される。本番デプロイの仕組みが整う
 *   タイミング(別 Plan)で、環境ガード or 実認証への切り替えを実施する。
 */
fun Application.authConfigure() {
    install(Authentication) {
        bearer("user") {
            realm = "mindstock"
            authenticate { credentials ->
                StubAuthProvider.resolve(credentials.token)
            }
        }
    }
}
