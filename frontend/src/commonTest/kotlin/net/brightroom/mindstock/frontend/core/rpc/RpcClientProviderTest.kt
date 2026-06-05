package net.brightroom.mindstock.frontend.core.rpc

import io.kotest.matchers.collections.shouldContainExactly
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class RpcClientProviderTest {
    @Test
    fun open_appends_app_and_bearer_subprotocols_as_separate_entries() =
        runTest {
            var captured: List<String> = emptyList()
            val engine =
                MockEngine { req ->
                    captured = req.headers.getAll(HttpHeaders.SecWebSocketProtocol) ?: emptyList()
                    respond("")
                }
            val provider = RpcClientProvider(HttpClient(engine), baseUrl = "ws://localhost")
            provider.probeHeaders("resident", "jwt-token")
            // "jwt-token" の base64url(no pad) = "and0LXRva2Vu"
            captured shouldContainExactly listOf("mindstock.v1", "mindstock.bearer.and0LXRva2Vu")
        }
}
