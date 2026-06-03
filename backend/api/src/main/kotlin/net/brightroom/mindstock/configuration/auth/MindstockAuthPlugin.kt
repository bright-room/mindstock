@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package net.brightroom.mindstock.configuration.auth

import com.auth0.jwk.JwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.JWTVerifier
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.response.respond
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.brightroom.mindstock.application.repository.resident.ResidentRepository
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import kotlin.time.Instant
import kotlin.uuid.Uuid

class MindstockAuthConfig {
    var jwkProvider: JwkProvider? = null
    var issuer: String? = null
    var audience: String? = null
    var residentRepository: ResidentRepository? = null
    var leewaySeconds: Long = 30
}

/**
 * JWT 検証 + [MindstockSession] 組み立てを担う Ktor plugin。
 *
 * Security Invariants(spec §セキュリティ不変条件):
 * 1. JWT 検証 crypto は自前で書かない → `com.auth0:java-jwt` の verify() 経由
 * 2. Algorithm は RSA256 固定([JwksKeyProvider] 経由)
 * 3. JWKS は cache + rate-limit(呼び出し側で [JwkProvider] を渡す前提)
 * 4. `withIssuer` / `withAudience` を必ず指定
 * 5. `acceptLeeway` を明示
 * 6. token 値を含む `Sec-WebSocket-Protocol` は response header に echo しない([WsSubprotocolEchoPlugin])
 *
 * 「JWT 有効だが Resident 未登録」は [ResidentRepository.findByAuth] の
 * [ResourceNotFoundException] のみ吸収し [MindstockSession.Unregistered] にする。
 * それ以外の例外(インフラ障害等)は握り潰さず伝播させる。
 * findByAuth は blocking JDBC transaction なので Dispatchers.IO に逃がす。
 */
val MindstockAuthPlugin =
    createApplicationPlugin(name = "MindstockAuth", createConfiguration = ::MindstockAuthConfig) {
        val jwkProvider = requireNotNull(pluginConfig.jwkProvider) { "jwkProvider required" }
        val issuer = requireNotNull(pluginConfig.issuer) { "issuer required" }
        val audience = requireNotNull(pluginConfig.audience) { "audience required" }
        val residentRepository = requireNotNull(pluginConfig.residentRepository) { "residentRepository required" }
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
            val expDate = decoded.expiresAt
            if (expDate == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@onCall
            }
            val exp = Instant.fromEpochMilliseconds(expDate.time) // java.util.Date -> kotlin.time.Instant
            val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject(sub))
            val callId = Uuid.random()

            val resident =
                withContext(Dispatchers.IO) {
                    try {
                        residentRepository.findByAuth(identity)
                    } catch (notFound: ResourceNotFoundException) {
                        null
                    }
                }
            val session =
                if (resident != null) {
                    MindstockSession.Registered(identity, resident.id, exp, callId)
                } else {
                    MindstockSession.Unregistered(identity, exp, callId)
                }
            call.attributes.put(MindstockSessionKey, session)
        }
    }
