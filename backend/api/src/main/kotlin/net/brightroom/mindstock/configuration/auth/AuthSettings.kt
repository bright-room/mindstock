package net.brightroom.mindstock.configuration.auth

import io.ktor.server.config.ApplicationConfig

/** JWT 検証に必要な外部 IdP 設定。未設定なら起動時に fail-fast する。 */
data class AuthSettings(
    val issuer: String,
    val audience: String,
    val jwksUrl: String,
)

/**
 * `external.auth` 配下の必須設定を読み、未設定/空文字なら案内付きで即時エラー。
 * デフォルト値は与えない(誤った既定値で起動する方が危険)。
 *
 * `MapApplicationConfig` はドット区切りキーをフラットに保持するため、
 * `.config("external.auth")` によるサブコンフィグ取り出しが動作しない環境を考慮し、
 * ルートコンフィグから `external.auth.<key>` で直接読む。
 */
fun requireAuthSettings(config: ApplicationConfig): AuthSettings {
    fun required(
        key: String,
        env: String,
    ): String {
        val value = config.propertyOrNull("external.auth.$key")?.getString()
        check(!value.isNullOrBlank()) {
            "external.auth.$key (env $env) が未設定です。`.env.zitadel` を生成しましたか?(`mise run up`)"
        }
        return value
    }
    return AuthSettings(
        issuer = required("issuer", "AUTH_ISSUER"),
        audience = required("audience", "AUTH_AUDIENCE"),
        jwksUrl = required("jwks-url", "AUTH_JWKS_URL"),
    )
}
