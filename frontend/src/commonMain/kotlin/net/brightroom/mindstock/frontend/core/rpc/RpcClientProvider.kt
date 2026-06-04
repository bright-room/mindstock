package net.brightroom.mindstock.frontend.core.rpc

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import kotlinx.rpc.krpc.ktor.client.KtorRpcClient
import kotlinx.rpc.krpc.ktor.client.installKrpc
import kotlinx.rpc.krpc.ktor.client.rpc
import kotlinx.rpc.krpc.serialization.json.json
import net.brightroom.mindstock.extensions.kotlinx.serialization.KrpcJson
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 認証済み Krpc クライアントを開く。各 @Rpc サービスのパス(/api/v1/<path>)に WS 接続し、
 * トークンを subprotocol で運ぶ。
 *
 * baseUrl は ws:// or wss://(kotlinx-rpc は scheme で transport を選ぶ)。
 */
@OptIn(ExperimentalEncodingApi::class)
class RpcClientProvider(
    http: HttpClient,
    private val baseUrl: String,
) {
    private val rpcHttp: HttpClient =
        http.config {
            installKrpc { serialization { json(KrpcJson) } }
            install(WebSockets)
        }
    private val rawHttp: HttpClient = http
    private val opened = mutableListOf<KtorRpcClient>()

    fun open(
        path: String,
        accessToken: String,
    ): KtorRpcClient {
        val b64 = encode(accessToken)
        val rpc =
            rpcHttp.rpc("$baseUrl/api/v1/$path") {
                headers.append(HttpHeaders.SecWebSocketProtocol, "mindstock.v1")
                headers.append(HttpHeaders.SecWebSocketProtocol, "mindstock.bearer.$b64")
            }
        opened += rpc
        return rpc
    }

    /** Test helper: 同一ヘッダで 1 回 GET し MockEngine に検査させる。 */
    internal suspend fun probeHeaders(
        path: String,
        accessToken: String,
    ) {
        val b64 = encode(accessToken)
        rawHttp.get("$baseUrl/api/v1/$path") {
            headers.append(HttpHeaders.SecWebSocketProtocol, "mindstock.v1")
            headers.append(HttpHeaders.SecWebSocketProtocol, "mindstock.bearer.$b64")
        }
    }

    fun closeAll() {
        opened.forEach { it.close("reauth or logout") }
        opened.clear()
    }

    private fun encode(token: String): String = Base64.UrlSafe.encode(token.encodeToByteArray()).trimEnd('=')
}
