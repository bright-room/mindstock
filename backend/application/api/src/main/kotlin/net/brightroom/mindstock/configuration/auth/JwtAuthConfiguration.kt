package net.brightroom.mindstock.configuration.auth

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.server.application.Application
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * `application.yaml` の `external.auth.*` から読み込んだ JWT 検証設定。
 */
data class JwtAuthSettings(
    val issuer: String,
    val audience: String,
    val jwksUrl: String,
) {
    companion object {
        fun from(app: Application): JwtAuthSettings {
            val cfg = app.environment.config.config("external.auth")
            return JwtAuthSettings(
                issuer = cfg.property("issuer").getString(),
                audience = cfg.property("audience").getString(),
                jwksUrl = cfg.property("jwks-url").getString(),
            )
        }
    }
}

/**
 * JWKS キャッシュ + rate-limit 付きの JwkProvider を構築する。
 *  - cached(10, 1 hour): 最大 10 鍵を 1 時間キャッシュ
 *  - rateLimited(10, 1 minute): 過剰アクセス抑制
 */
fun buildJwkProvider(settings: JwtAuthSettings): JwkProvider =
    JwkProviderBuilder(URL(settings.jwksUrl))
        .cached(10, 1, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()
