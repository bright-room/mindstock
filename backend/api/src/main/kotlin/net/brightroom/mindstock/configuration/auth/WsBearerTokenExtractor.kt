package net.brightroom.mindstock.configuration.auth

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * RPC は WebSocket トランスポート上で動くが、Browser の WebSocket API は
 * Authorization ヘッダを設定できない。そのため `Sec-WebSocket-Protocol` の
 * カスタムサブプロトコル `mindstock.bearer.<base64url(jwt)>` で token を運ぶ。
 *
 * 本番フロントもテストもこの経路一本に統一している(REST/Authorization 経路は持たない)。
 */
internal object WsBearerTokenExtractor {
    private const val WS_PROTOCOL_BEARER_PREFIX = "mindstock.bearer."

    /**
     * Sec-WebSocket-Protocol から生の JWT 文字列を取り出す。MindstockAuthPlugin 用。
     *
     * 戻り値の `null` は「使える bearer token が無い」を一律に表す(未指定 / bearer entry が
     * 複数で曖昧 / base64 decode 失敗)。認証境界では理由を区別せず一律 401 に倒すのが
     * 正しい(理由の出し分けは情報漏洩になる)ため、これは意図した契約。`internal` 限定で
     * 公開 API ではない。
     */
    fun extractRaw(call: ApplicationCall): String? {
        val protocols =
            call.request.headers
                .getAll(HttpHeaders.SecWebSocketProtocol)
                .orEmpty()
        val entries = protocols.flatMap { it.split(",") }.map { it.trim() }
        val bearerEntries = entries.filter { it.startsWith(WS_PROTOCOL_BEARER_PREFIX) }
        // bearer entry が 0 個なら不在、2 個以上なら曖昧 → どちらも fail-closed で null。
        val bearerEntry = bearerEntries.singleOrNull() ?: return null
        val b64 = bearerEntry.removePrefix(WS_PROTOCOL_BEARER_PREFIX)
        return runCatching {
            String(Base64.getUrlDecoder().decode(b64), StandardCharsets.UTF_8)
        }.getOrNull()
    }
}
