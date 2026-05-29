package net.brightroom.mindstock.configuration.auth

import io.ktor.http.HttpHeaders
import io.ktor.http.auth.AuthScheme
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * RPC は WebSocket トランスポート上で動くが、Browser の WebSocket API は
 * Authorization ヘッダを設定できない。そのため `Sec-WebSocket-Protocol` の
 * カスタムサブプロトコル `mindstock.bearer.<base64url(jwt)>` で token を運ぶ。
 *
 * REST 互換性とテスト容易性のため Authorization ヘッダにも対応する(優先)。
 */
object WsBearerTokenExtractor {
    private const val WS_PROTOCOL_BEARER_PREFIX = "mindstock.bearer."

    /**
     * Authorization ヘッダまたは Sec-WebSocket-Protocol から
     * 生の JWT 文字列を取り出す。MindstockAuthPlugin 用。
     */
    fun extractRaw(call: ApplicationCall): String? {
        call.request.header(HttpHeaders.Authorization)?.let { value ->
            val parts = value.trim().split(" ", limit = 2)
            if (parts.size == 2 && parts[0].equals(AuthScheme.Bearer, ignoreCase = true)) {
                return parts[1].trim()
            }
        }
        val protocols =
            call.request.headers
                .getAll(HttpHeaders.SecWebSocketProtocol)
                .orEmpty()
        val entries = protocols.flatMap { it.split(",") }.map { it.trim() }
        val bearerEntry = entries.firstOrNull { it.startsWith(WS_PROTOCOL_BEARER_PREFIX) } ?: return null
        val b64 = bearerEntry.removePrefix(WS_PROTOCOL_BEARER_PREFIX)
        return runCatching {
            String(Base64.getUrlDecoder().decode(b64), StandardCharsets.UTF_8)
        }.getOrNull()
    }
}
