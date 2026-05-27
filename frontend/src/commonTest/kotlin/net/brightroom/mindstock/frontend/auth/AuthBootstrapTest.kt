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
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthBootstrapTest {
    @AfterTest fun cleanup() { TokenStore.clear() }

    private fun freshAuthClient(
        refreshJson: String = """{"access_token":"AT2","refresh_token":"RT2","id_token":"IT2","expires_in":3600}""",
        status: HttpStatusCode = HttpStatusCode.OK,
    ): AuthClient {
        val http = HttpClient(MockEngine {
            respond(
                refreshJson,
                status,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        })
        return AuthClient(http, "https://idp.example", "c1", "x")
    }

    @Test
    fun no_tokens_results_in_logged_out() = runTest {
        TokenStore.clear()
        val bs = AuthBootstrap(freshAuthClient(), ping = { PingResult.Success })
        assertEquals(AuthState.LoggedOut, bs.start(now = Instant.fromEpochSeconds(100)))
    }

    @Test
    fun valid_tokens_and_ping_success_results_in_ready() = runTest {
        TokenStore.save(Tokens("AT", "RT", "IT", Instant.fromEpochSeconds(200_000)))
        val bs = AuthBootstrap(freshAuthClient(), ping = { PingResult.Success })
        val state = bs.start(now = Instant.fromEpochSeconds(100_000))
        assertTrue(state is AuthState.Ready)
        assertEquals("AT", state.tokens.accessToken)
    }

    @Test
    fun valid_tokens_and_ping_unauthorized_results_in_need_register() = runTest {
        TokenStore.save(Tokens("AT", "RT", "IT", Instant.fromEpochSeconds(200_000)))
        val bs = AuthBootstrap(freshAuthClient(), ping = { PingResult.Unauthorized })
        assertEquals(AuthState.NeedRegister, bs.start(now = Instant.fromEpochSeconds(100_000)))
    }

    @Test
    fun expiring_tokens_trigger_refresh_then_ready() = runTest {
        TokenStore.save(Tokens("AT", "RT", "IT", Instant.fromEpochSeconds(100_030))) // 30s until expiry
        val bs = AuthBootstrap(freshAuthClient(), ping = { PingResult.Success })
        val state = bs.start(now = Instant.fromEpochSeconds(100_000))
        assertTrue(state is AuthState.Ready)
        assertEquals("AT2", state.tokens.accessToken)
        assertEquals("AT2", TokenStore.load()!!.accessToken)
    }

    @Test
    fun refresh_failure_results_in_logged_out_and_clear() = runTest {
        TokenStore.save(Tokens("AT", "RT", "IT", Instant.fromEpochSeconds(100_030)))
        val bs = AuthBootstrap(
            freshAuthClient("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest),
            ping = { PingResult.Success },
        )
        assertEquals(AuthState.LoggedOut, bs.start(now = Instant.fromEpochSeconds(100_000)))
        assertNull(TokenStore.load())
    }
}
