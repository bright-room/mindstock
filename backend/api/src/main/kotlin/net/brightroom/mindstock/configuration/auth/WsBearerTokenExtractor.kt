package net.brightroom.mindstock.configuration.auth

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header

/**
 * 生の JWT を取り出す。Browser の WebSocket API は Authorization ヘッダを付けられないため
 * `Sec-WebSocket-Protocol` の `mindstock.bearer.<base64url(jwt)>` でも運ぶ。
 * Authorization ヘッダを優先(REST 互換 / テスト容易性)。
 */
object WsBearerTokenExtractor {
    fun extractRaw(call: ApplicationCall): String? = authorizationBearer(call) ?: webSocketProtocolBearer(call)

    private fun authorizationBearer(call: ApplicationCall): String? {
        val header = call.request.header(HttpHeaders.Authorization) ?: return null
        return AuthorizationHeader(header).bearerToken()
    }

    private fun webSocketProtocolBearer(call: ApplicationCall): String? = WebSocketProtocols.from(call).bearerToken()
}
