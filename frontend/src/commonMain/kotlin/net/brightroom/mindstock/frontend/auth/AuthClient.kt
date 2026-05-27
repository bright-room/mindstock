package net.brightroom.mindstock.frontend.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class AuthClient(
    private val http: HttpClient,
    private val issuer: String,
    private val clientId: String,
    private val redirectUri: String,
) {
    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String = "",
        @SerialName("id_token") val idToken: String = "",
        @SerialName("expires_in") val expiresIn: Long,
    )

    @Serializable
    private data class ErrorResponse(
        val error: String,
        @SerialName("error_description") val errorDescription: String? = null,
    )

    suspend fun exchangeCode(code: String, codeVerifier: String, now: Instant = Clock.System.now()): Tokens =
        postToken(
            params = Parameters.build {
                append("grant_type", "authorization_code")
                append("code", code)
                append("client_id", clientId)
                append("redirect_uri", redirectUri)
                append("code_verifier", codeVerifier)
            },
            now = now,
        )

    suspend fun refresh(refreshToken: String, now: Instant = Clock.System.now()): Tokens =
        postToken(
            params = Parameters.build {
                append("grant_type", "refresh_token")
                append("refresh_token", refreshToken)
                append("client_id", clientId)
            },
            now = now,
        )

    private suspend fun postToken(params: Parameters, now: Instant): Tokens {
        val resp: HttpResponse = http.submitForm(url = "$issuer/oauth/v2/token", formParameters = params)
        if (!resp.status.isSuccess()) {
            val text = resp.bodyAsText()
            val err = runCatching { JSON.decodeFromString(ErrorResponse.serializer(), text) }.getOrNull()
            val errorCode = err?.error ?: "http_${resp.status.value}"
            val desc = err?.errorDescription ?: text.take(200)
            val reauth = errorCode == "invalid_grant"
            throw OidcException(errorCode, desc, reauthRequired = reauth)
        }
        val text = resp.bodyAsText()
        val tr = JSON.decodeFromString(TokenResponse.serializer(), text)
        return Tokens.fromTokenResponse(tr.accessToken, tr.refreshToken, tr.idToken, tr.expiresIn, now)
    }

    companion object {
        internal val JSON = Json { ignoreUnknownKeys = true }

        fun buildAuthorizeUrl(
            issuer: String,
            clientId: String,
            redirectUri: String,
            scope: String,
            state: String,
            codeChallenge: String,
        ): String {
            val base = "$issuer/oauth/v2/authorize"
            val q = listOf(
                "response_type" to "code",
                "client_id" to clientId,
                "redirect_uri" to redirectUri,
                "scope" to scope,
                "state" to state,
                "code_challenge" to codeChallenge,
                "code_challenge_method" to "S256",
            ).joinToString("&") { (k, v) -> "$k=${v.encodeURLParameter()}" }
            return "$base?$q"
        }

        fun endSessionUrl(issuer: String, idToken: String, postLogoutRedirectUri: String): String {
            val q = listOf(
                "id_token_hint" to idToken,
                "post_logout_redirect_uri" to postLogoutRedirectUri,
            ).joinToString("&") { (k, v) -> "$k=${v.encodeURLParameter()}" }
            return "$issuer/oidc/v1/end_session?$q"
        }
    }
}
