@file:Suppress("DEPRECATION")

package net.brightroom.mindstock.configuration.auth

import com.auth0.jwk.JwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.JWTVerifier
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.response.respond
import kotlinx.datetime.Instant
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class MindstockAuthConfig {
    var jwkProvider: JwkProvider? = null
    var issuer: String? = null
    var audience: String? = null
    var userRepository: UserRepository? = null
    var database: Database? = null
    var leewaySeconds: Long = 30
}

/**
 * JWT 検証 + [MindstockSession] 組み立てを担う Route-scoped Ktor plugin。
 *
 * Security Invariants(spec §4.6):
 * 1. JWT 検証 crypto は自前で書かない → `com.auth0:java-jwt` の `JWT.require(...).build().verify()` 経由
 * 2. Algorithm は RSA256 固定(`JwksKeyProvider` 経由)
 * 3. JWKS は cache + rate-limit 必須(呼び出し側で `JwkProviderBuilder` を渡す前提)
 * 4. `withIssuer` / `withAudience` を必ず指定
 * 5. `acceptLeeway(30)` を明示
 * 6. validate 相当の DB アクセスは `newSuspendedTransaction`
 * 7. token 値を含む `Sec-WebSocket-Protocol` は response header に echo しない([WsSubprotocolEchoPlugin])
 */
@OptIn(ExperimentalUuidApi::class)
val MindstockAuthPlugin =
    createRouteScopedPlugin(name = "MindstockAuth", createConfiguration = ::MindstockAuthConfig) {
        val jwkProvider = requireNotNull(pluginConfig.jwkProvider) { "jwkProvider required" }
        val issuer = requireNotNull(pluginConfig.issuer) { "issuer required" }
        val audience = requireNotNull(pluginConfig.audience) { "audience required" }
        val userRepository = requireNotNull(pluginConfig.userRepository) { "userRepository required" }
        val database = requireNotNull(pluginConfig.database) { "database required" }
        val leewaySeconds = pluginConfig.leewaySeconds

        val verifier: JWTVerifier =
            JWT
                .require(Algorithm.RSA256(JwksKeyProvider(jwkProvider)))
                .withIssuer(issuer)
                .withAudience(audience)
                .acceptLeeway(leewaySeconds)
                .build()

        onCall { call ->
            val token = WsBearerTokenExtractor.extractRaw(call)
            if (token == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@onCall
            }
            val decoded = runCatching { verifier.verify(token) }.getOrNull()
            if (decoded == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@onCall
            }
            val sub = decoded.subject
            if (sub.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized)
                return@onCall
            }
            val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject(sub))
            val userId =
                newSuspendedTransaction(db = database) {
                    userRepository.findByAuthIdentity(identity)?.id
                }
            val expDate =
                decoded.expiresAt
                    ?: run {
                        call.respond(HttpStatusCode.Unauthorized)
                        return@onCall
                    }
            val session =
                MindstockSession(
                    identity = identity,
                    userId = userId,
                    exp = Instant.fromEpochMilliseconds(expDate.time),
                    callId = Uuid.random(),
                )
            call.attributes.put(MindstockSessionKey, session)
        }
    }
