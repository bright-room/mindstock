package net.brightroom.mindstock.configuration.auth

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer

/**
 * Ktor Authentication プラグインを install し、Bearer token を [StubAuthProvider] で検証する。
 * 本格的な認証 (JWT 検証等) は別 Plan で置き換える。
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
