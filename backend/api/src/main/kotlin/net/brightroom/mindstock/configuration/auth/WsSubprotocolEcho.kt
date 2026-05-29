package net.brightroom.mindstock.configuration.auth

import io.ktor.http.HttpHeaders
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.response.header

/**
 * WebSocket handshake で client が Sec-WebSocket-Protocol を提示してきた場合、
 * 受理した subprotocol を 1 つ response に echo しないと WHATWG WebSocket 仕様により
 * ブラウザは接続を fail させる。
 *
 * - `mindstock.v1` (アプリプロトコル識別子) は echo する。
 * - `mindstock.bearer.*` (JWT を含む) は echo しない。token を response header や
 *   中間 proxy のログに漏らさないため。
 *
 * kotlinx-rpc-krpc-ktor-server の `rpc(path)` builder は内部で `webSocket(...)` を呼ぶが、
 * subprotocol 応答を制御する API を公開していないため、本 plugin で response header を
 * 上書きする。Ktor の WebSockets plugin は upgrade 応答時に `call.response.headers` を
 * 取り込むので、上書きが Sec-WebSocket-Protocol 値として出力される。
 */
val WsSubprotocolEchoPlugin =
    createApplicationPlugin(name = "WsSubprotocolEcho") {
        onCall { call ->
            val upgrade = call.request.headers[HttpHeaders.Upgrade]
            if (upgrade == null || !upgrade.equals("websocket", ignoreCase = true)) return@onCall
            val offered =
                call.request.headers
                    .getAll(HttpHeaders.SecWebSocketProtocol)
                    ?.flatMap { it.split(",") }
                    ?.map { it.trim() }
                    .orEmpty()
            if ("mindstock.v1" in offered) {
                call.response.header(HttpHeaders.SecWebSocketProtocol, "mindstock.v1")
            }
        }
    }
