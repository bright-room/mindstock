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
    fun connect_sends_app_and_bearer_subprotocols_to_single_endpoint() =
        runTest {
            var capturedPath = ""
            var captured: List<String> = emptyList()
            val engine =
                MockEngine { req ->
                    capturedPath = req.url.encodedPath
                    captured = req.headers.getAll(HttpHeaders.SecWebSocketProtocol) ?: emptyList()
                    respond("")
                }
            val provider = RpcClientProvider(HttpClient(engine), baseUrl = "ws://localhost")
            provider.probeHeaders("jwt-token")
            capturedPath shouldBeEndpoint "/api/rpc"
            // "jwt-token" の base64url(no pad) = "and0LXRva2Vu"
            captured shouldContainExactly listOf("mindstock.v1", "mindstock.bearer.and0LXRva2Vu")
        }
}

private infix fun String.shouldBeEndpoint(expected: String) {
    if (this != expected) throw AssertionError("path was '$this', expected '$expected'")
}
