package net.brightroom.mindstock.configuration.auth

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer

/**
 * Ktor Authentication プラグインを install する(一時的に Stub 実装のまま)。
 * Task 6 で jwt("user") + jwt("user-public") に置き換える。
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
