package net.brightroom.mindstock.frontend.rpc

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

@OptIn(ExperimentalEncodingApi::class)
class RpcClientFactory(
    http: HttpClient,
    private val baseUrl: String,
) {
    /** Pre-configured client with Krpc + WebSockets installed; reused across opens. */
    private val rpcHttp: HttpClient = http.config {
        installKrpc { serialization { json(KrpcJson) } }
        install(WebSockets)
    }
    private val rawHttp: HttpClient = http

    private val opened = mutableListOf<KtorRpcClient>()

    /** 認証済み Krpc クライアントを開く。 */
    fun open(path: String, accessToken: String): KtorRpcClient {
        val b64 = encodeTokenBase64Url(accessToken)
        val rpc = rpcHttp.rpc("$baseUrl/api/v1/$path") {
            // Browser WebSocket は subprotocols を別エントリで受け取る必要がある。
            // 1 つの値にカンマ区切りで詰めると「空白入り subprotocol 名」と扱われ拒否される。
            headers.append(HttpHeaders.SecWebSocketProtocol, "mindstock.v1")
            headers.append(HttpHeaders.SecWebSocketProtocol, "mindstock.bearer.$b64")
        }
        opened += rpc
        return rpc
    }

    /** Test helper: send one HTTP GET with the same headers so MockEngine can inspect them. */
    internal suspend fun openRaw(path: String, accessToken: String) {
        val b64 = encodeTokenBase64Url(accessToken)
        rawHttp.get("$baseUrl/api/v1/$path") {
            headers.append(HttpHeaders.SecWebSocketProtocol, "mindstock.v1")
            headers.append(HttpHeaders.SecWebSocketProtocol, "mindstock.bearer.$b64")
        }
    }

    fun closeAll() {
        opened.forEach { it.close("reauth or logout") }
        opened.clear()
    }

    private fun encodeTokenBase64Url(token: String): String =
        Base64.UrlSafe.encode(token.encodeToByteArray()).trimEnd('=')
}
