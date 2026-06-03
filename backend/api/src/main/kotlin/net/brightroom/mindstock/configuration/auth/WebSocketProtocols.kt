package net.brightroom.mindstock.configuration.auth

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.jvm.JvmInline

/**
 * `Sec-WebSocket-Protocol` ヘッダ群を「カンマ分割・trim 済みエントリ集合」として扱う。
 * アプリプロトコル判定([has])と bearer token 抽出([bearerToken])を閉じ込め、
 * [WsBearerTokenExtractor] と [WsSubprotocolEchoPlugin] で共用する。
 */
@JvmInline
value class WebSocketProtocols private constructor(
    private val entries: List<String>,
) {
    /** 指定 subprotocol が提示されているか。 */
    fun has(protocol: String): Boolean = protocol in entries

    /**
     * `mindstock.bearer.<base64url(jwt)>` がちょうど 1 件あれば decode した JWT を返す。
     * 無い、または複数あって曖昧な場合は null(認証境界では曖昧な資格情報を受理しない)。
     */
    fun bearerToken(): String? {
        val bearerEntries = entries.filter { it.startsWith(BEARER_PREFIX) }
        if (bearerEntries.size != 1) return null
        return decodeBase64Url(bearerEntries.single().removePrefix(BEARER_PREFIX))
    }

    companion object {
        /** アプリプロトコル識別子(echo 対象)。 */
        const val APP_PROTOCOL: String = "mindstock.v1"

        /** JWT を運ぶ bearer subprotocol の prefix(echo してはならない)。 */
        private const val BEARER_PREFIX: String = "mindstock.bearer."

        fun from(call: ApplicationCall): WebSocketProtocols =
            from(
                call.request.headers
                    .getAll(HttpHeaders.SecWebSocketProtocol)
                    .orEmpty(),
            )

        fun from(rawHeaders: List<String>): WebSocketProtocols = WebSocketProtocols(rawHeaders.flatMap { it.split(",") }.map { it.trim() })

        private fun decodeBase64Url(value: String): String? =
            runCatching { String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8) }.getOrNull()
    }
}
