package net.brightroom.mindstock.configuration.auth

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.di.dependencies
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.domain.repository.user.UserRepository
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Ktor Authentication プラグインで JWT (access_token) を JWKS 検証する。
 *
 * - `"user"`: JWT 検証 + User 登録チェック。未登録なら 401。通常 RPC 用。
 * - `"user-public"`: JWT 検証のみ。User 未登録でも通る。register 専用。
 *
 * トークンの取得元は [WsBearerTokenExtractor] が決める(Authorization ヘッダ / Sec-WebSocket-Protocol)。
 */
fun Application.authConfigure() {
    val settings = JwtAuthSettings.from(this)
    val jwkProvider = buildJwkProvider(settings)

    val userRepository: UserRepository by dependencies
    val database: Database by dependencies

    install(Authentication) {
        jwt("user") {
            realm = "mindstock"
            authHeader { call -> WsBearerTokenExtractor.extract(call) }
            verifier(jwkProvider, settings.issuer) {
                acceptLeeway(30)
                withAudience(settings.audience)
            }
            validate { credential ->
                val sub = credential.payload.subject ?: return@validate null
                val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject(sub))
                val exists =
                    transaction(database) {
                        userRepository.findByAuthIdentity(identity) != null
                    }
                if (!exists) return@validate null
                MindstockPrincipal(identity)
            }
        }
        jwt("user-public") {
            realm = "mindstock"
            authHeader { call -> WsBearerTokenExtractor.extract(call) }
            verifier(jwkProvider, settings.issuer) {
                acceptLeeway(30)
                withAudience(settings.audience)
            }
            validate { credential ->
                val sub = credential.payload.subject ?: return@validate null
                MindstockPrincipal(AuthIdentity(AuthProvider.ZITADEL, AuthSubject(sub)))
            }
        }
    }
}
