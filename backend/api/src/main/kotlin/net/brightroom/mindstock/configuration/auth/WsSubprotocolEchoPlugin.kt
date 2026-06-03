package net.brightroom.mindstock.configuration.auth

import io.ktor.http.HttpHeaders
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.response.header

/**
 * WHATWG WebSocket 仕様上、client が Sec-WebSocket-Protocol を提示したら server は
 * 受理した subprotocol を 1 つ echo しないとブラウザが接続を fail させる。
 *
 * - [WebSocketProtocols.APP_PROTOCOL] (mindstock.v1) が提示されていれば、それだけを echo する。
 * - bearer subprotocol (JWT を含む) は echo しない。token を response header や
 *   中間 proxy のログに漏らさないため(常に固定の APP_PROTOCOL 定数だけを書き、
 *   リクエスト由来の値は一切 response に書かない)。
 *
 * 判定は「app protocol が提示されたか」のみ。Sec-WebSocket-Protocol は WS handshake でしか
 * 送られないため、通常リクエストには影響しない。
 */
val WsSubprotocolEchoPlugin =
    createApplicationPlugin(name = "WsSubprotocolEcho") {
        onCall { call ->
            val protocols = WebSocketProtocols.from(call)
            if (!protocols.has(WebSocketProtocols.APP_PROTOCOL)) return@onCall
            call.response.header(HttpHeaders.SecWebSocketProtocol, WebSocketProtocols.APP_PROTOCOL)
        }
    }
