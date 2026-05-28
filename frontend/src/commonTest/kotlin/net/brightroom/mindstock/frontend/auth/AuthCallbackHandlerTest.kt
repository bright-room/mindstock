package net.brightroom.mindstock.frontend.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuthCallbackHandlerTest {
    private fun mockClient(json: String): HttpClient =
        HttpClient(
            MockEngine {
                respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            },
        )

    @Test
    fun mismatched_state_rejected() =
        runTest {
            val ac = AuthClient(mockClient("{}"), "https://idp.example", "c1", "x")
            val h = AuthCallbackHandler(ac, savedState = "expected-S", savedVerifier = "V")
            assertFailsWith<OidcException> { h.handle(receivedState = "wrong-S", code = "C", now = Instant.fromEpochSeconds(0)) }
        }

    @Test
    fun missing_saved_state_rejected() =
        runTest {
            val ac = AuthClient(mockClient("{}"), "https://idp.example", "c1", "x")
            val h = AuthCallbackHandler(ac, savedState = null, savedVerifier = "V")
            assertFailsWith<OidcException> { h.handle(receivedState = "S", code = "C", now = Instant.fromEpochSeconds(0)) }
        }

    @Test
    fun missing_saved_verifier_rejected() =
        runTest {
            val ac = AuthClient(mockClient("{}"), "https://idp.example", "c1", "x")
            val h = AuthCallbackHandler(ac, savedState = "S", savedVerifier = null)
            assertFailsWith<OidcException> { h.handle(receivedState = "S", code = "C", now = Instant.fromEpochSeconds(0)) }
        }

    @Test
    fun matched_state_exchanges_code_and_returns_tokens() =
        runTest {
            val ac =
                AuthClient(
                    mockClient("""{"access_token":"AT","refresh_token":"RT","id_token":"IT","expires_in":3600}"""),
                    "https://idp.example",
                    "c1",
                    "x",
                )
            val h = AuthCallbackHandler(ac, savedState = "S", savedVerifier = "V")
            val tokens = h.handle(receivedState = "S", code = "C", now = Instant.fromEpochSeconds(100))
            assertEquals("AT", tokens.accessToken)
        }
}
