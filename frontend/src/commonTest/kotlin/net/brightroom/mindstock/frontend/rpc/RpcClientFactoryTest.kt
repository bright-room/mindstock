package net.brightroom.mindstock.frontend.rpc

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RpcClientFactoryTest {
    @Test
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    fun secWebSocketProtocol_includes_mindstock_v1_and_bearer_token() =
        runTest {
            var capturedProtocols: List<String>? = null
            val engine =
                MockEngine { req ->
                    capturedProtocols = req.headers.getAll(HttpHeaders.SecWebSocketProtocol)
                    respondError(HttpStatusCode.NotImplemented)
                }
            val http = HttpClient(engine)
            val factory = RpcClientFactory(http, baseUrl = "http://localhost:8080")
            runCatching { factory.openRaw(path = "user", accessToken = "MY.JWT.TOKEN") }
            // Browser WS は subprotocols を配列で扱うため別々の header エントリで append している。
            // ktor が後でカンマ結合するか別 entry のまま送るかは engine 次第なので、両形式を受ける。
            val parts =
                (capturedProtocols ?: error("Sec-WebSocket-Protocol not set"))
                    .flatMap { it.split(",") }
                    .map { it.trim() }
            assertEquals("mindstock.v1", parts[0])
            assertTrue(parts[1].startsWith("mindstock.bearer."), "got $parts")
            val b64 = parts[1].removePrefix("mindstock.bearer.")
            val decoded = base64UrlNoPadDecodeForTest(b64).decodeToString()
            assertEquals("MY.JWT.TOKEN", decoded)
        }
}

@kotlin.io.encoding.ExperimentalEncodingApi
private fun base64UrlNoPadDecodeForTest(s: String): ByteArray {
    val padded = s + "=".repeat((4 - s.length % 4) % 4)
    return kotlin.io.encoding.Base64.UrlSafe
        .decode(padded)
}
