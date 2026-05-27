package net.brightroom.mindstock.frontend.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AuthClientTest {
    private fun client(handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData): HttpClient =
        HttpClient(MockEngine { req -> handler(req) })

    @Test
    fun buildAuthorizeUrl_includes_pkce_state_scope() {
        val url = AuthClient.buildAuthorizeUrl(
            issuer = "https://idp.example",
            clientId = "c1",
            redirectUri = "https://app.example/auth/callback",
            scope = "openid profile",
            state = "state-xyz",
            codeChallenge = "chal-abc",
        )
        assertTrue(url.startsWith("https://idp.example/oauth/v2/authorize?"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("client_id=c1"))
        assertTrue(url.contains("redirect_uri=https%3A%2F%2Fapp.example%2Fauth%2Fcallback"))
        assertTrue(url.contains("scope=openid+profile") || url.contains("scope=openid%20profile"))
        assertTrue(url.contains("state=state-xyz"))
        assertTrue(url.contains("code_challenge=chal-abc"))
        assertTrue(url.contains("code_challenge_method=S256"))
    }

    @Test
    fun exchangeCode_posts_form_and_parses_token_response() = runTest {
        val http = client { req ->
            assertEquals("https://idp.example/oauth/v2/token", req.url.toString())
            val body = (req.body as io.ktor.http.content.OutgoingContent.ByteArrayContent).bytes().decodeToString()
            assertTrue(body.contains("grant_type=authorization_code"))
            assertTrue(body.contains("code=THE_CODE"))
            assertTrue(body.contains("client_id=c1"))
            assertTrue(body.contains("redirect_uri=https%3A%2F%2Fapp.example%2Fauth%2Fcallback"))
            assertTrue(body.contains("code_verifier=THE_VERIFIER"))
            respond(
                """{"access_token":"AT","refresh_token":"RT","id_token":"IT","expires_in":3600,"token_type":"Bearer"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val ac = AuthClient(http, issuer = "https://idp.example", clientId = "c1", redirectUri = "https://app.example/auth/callback")
        val tokens = ac.exchangeCode(code = "THE_CODE", codeVerifier = "THE_VERIFIER", now = Instant.fromEpochSeconds(100))
        assertEquals("AT", tokens.accessToken)
        assertEquals("RT", tokens.refreshToken)
        assertEquals("IT", tokens.idToken)
        assertEquals(Instant.fromEpochSeconds(3700), tokens.expiresAt)
    }

    @Test
    fun refresh_uses_refresh_token_grant() = runTest {
        val http = client { req ->
            val body = (req.body as io.ktor.http.content.OutgoingContent.ByteArrayContent).bytes().decodeToString()
            assertTrue(body.contains("grant_type=refresh_token"))
            assertTrue(body.contains("refresh_token=OLD_RT"))
            assertTrue(body.contains("client_id=c1"))
            respond(
                """{"access_token":"AT2","refresh_token":"NEW_RT","id_token":"IT2","expires_in":3600,"token_type":"Bearer"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val ac = AuthClient(http, issuer = "https://idp.example", clientId = "c1", redirectUri = "x")
        val tokens = ac.refresh("OLD_RT", now = Instant.fromEpochSeconds(100))
        assertEquals("AT2", tokens.accessToken)
        assertEquals("NEW_RT", tokens.refreshToken)
    }

    @Test
    fun refresh_invalid_grant_throws_reauth_required() = runTest {
        val http = client { _ ->
            respond(
                """{"error":"invalid_grant","error_description":"refresh token expired"}""",
                HttpStatusCode.BadRequest,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val ac = AuthClient(http, issuer = "https://idp.example", clientId = "c1", redirectUri = "x")
        val ex = assertFailsWith<OidcException> { ac.refresh("OLD_RT") }
        assertEquals("invalid_grant", ex.errorCode)
        assertTrue(ex.reauthRequired)
    }

    @Test
    fun endSessionUrl_includes_id_token_hint_and_post_logout() {
        val url = AuthClient.endSessionUrl(
            issuer = "https://idp.example",
            idToken = "IT",
            postLogoutRedirectUri = "https://app.example/",
        )
        assertTrue(url.startsWith("https://idp.example/oidc/v1/end_session?"))
        assertTrue(url.contains("id_token_hint=IT"))
        assertTrue(url.contains("post_logout_redirect_uri=https%3A%2F%2Fapp.example%2F"))
    }
}
