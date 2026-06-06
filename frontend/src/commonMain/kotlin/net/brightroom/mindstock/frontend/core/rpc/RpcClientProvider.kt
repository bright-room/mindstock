package net.brightroom.mindstock.frontend.core.rpc

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import kotlinx.rpc.annotations.Rpc
import kotlinx.rpc.krpc.ktor.client.KtorRpcClient
import kotlinx.rpc.krpc.ktor.client.installKrpc
import kotlinx.rpc.krpc.ktor.client.rpc
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.rpc.withService
import net.brightroom.mindstock.extensions.kotlinx.serialization.KrpcJson
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 認証済み Krpc クライアントを単一接続で開く。全サービスを 1 本の WS(/api/rpc)に相乗りさせ、
 * [service] で各 @Rpc サービスを取り出す。トークンは subprotocol で運ぶ。
 * baseUrl は ws:// or wss://。
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

    @PublishedApi
    internal var client: KtorRpcClient? = null

    /** 単一の認証済み接続を開く(既存があれば閉じて張り直す)。 */
    fun connect(accessToken: String) {
        close()
        client = rpcHttp.rpc("$baseUrl/api/rpc") { appendAuthSubprotocols(accessToken) }
    }

    /** 接続済み client からサービスを取得。connect 前に呼ぶと例外。 */
    inline fun <@Rpc reified T : Any> service(): T = requireNotNull(client) { "rpc not connected" }.withService<T>()

    /** Test helper: 同一ヘッダで 1 回 GET し MockEngine に検査させる。 */
    internal suspend fun probeHeaders(accessToken: String) {
        rawHttp.get("$baseUrl/api/rpc") { appendAuthSubprotocols(accessToken) }
    }

    fun close() {
        client?.close("reauth or logout")
        client = null
    }

    /** WS subprotocol にアプリ識別子と bearer トークンを別エントリで載せる(connect / probe 共通)。 */
    private fun HttpRequestBuilder.appendAuthSubprotocols(accessToken: String) {
        val b64 = encode(accessToken)
        headers.append(HttpHeaders.SecWebSocketProtocol, "mindstock.v1")
        headers.append(HttpHeaders.SecWebSocketProtocol, "mindstock.bearer.$b64")
    }

    private fun encode(token: String): String = Base64.UrlSafe.encode(token.encodeToByteArray()).trimEnd('=')
}
