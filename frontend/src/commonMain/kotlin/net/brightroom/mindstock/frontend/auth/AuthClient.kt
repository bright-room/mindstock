package net.brightroom.mindstock.frontend.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
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

    suspend fun exchangeCode(
        code: String,
        codeVerifier: String,
        now: Instant = Clock.System.now(),
    ): Tokens =
        postToken(
            Parameters.build {
                append("grant_type", "authorization_code")
                append("code", code)
                append("client_id", clientId)
                append("redirect_uri", redirectUri)
                append("code_verifier", codeVerifier)
            },
            now,
        )

    suspend fun refresh(
        refreshToken: String,
        now: Instant = Clock.System.now(),
    ): Tokens =
        postToken(
            Parameters.build {
                append("grant_type", "refresh_token")
                append("refresh_token", refreshToken)
                append("client_id", clientId)
            },
            now,
        )

    private suspend fun postToken(
        params: Parameters,
        now: Instant,
    ): Tokens {
        val resp: HttpResponse = http.submitForm(url = "$issuer/oauth/v2/token", formParameters = params)
        if (!resp.status.isSuccess()) {
            val text = resp.bodyAsText()
            val err = runCatching { JSON.decodeFromString(ErrorResponse.serializer(), text) }.getOrNull()
            val errorCode = err?.error ?: "http_${resp.status.value}"
            val desc = err?.errorDescription ?: text.take(200)
            throw OidcException(errorCode, desc, reauthRequired = errorCode == "invalid_grant")
        }
        val text = resp.bodyAsText()
        val tr =
            runCatching { JSON.decodeFromString(TokenResponse.serializer(), text) }
                .getOrElse { throw OidcException("parse_error", it.message?.take(200), reauthRequired = false) }
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
            val q =
                listOf(
                    "response_type" to "code",
                    "client_id" to clientId,
                    "redirect_uri" to redirectUri,
                    "scope" to scope,
                    "state" to state,
                    "code_challenge" to codeChallenge,
                    "code_challenge_method" to "S256",
                ).joinToString("&") { (k, v) -> "$k=${v.encodeURLParameter()}" }
            return "$issuer/oauth/v2/authorize?$q"
        }
    }
}
