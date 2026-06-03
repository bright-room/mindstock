package net.brightroom.mindstock.configuration.auth

import io.ktor.http.auth.AuthScheme
import kotlin.jvm.JvmInline

/**
 * `Authorization` ヘッダの生値をラップし、Bearer token 抽出を閉じ込める。
 */
@JvmInline
value class AuthorizationHeader(
    private val raw: String,
) {
    /** `Bearer <token>` 形式なら token を返す。それ以外は null。 */
    fun bearerToken(): String? {
        val parts = raw.trim().split(" ", limit = 2)
        if (parts.size != 2) return null
        val (scheme, credentials) = parts
        if (!scheme.equals(AuthScheme.Bearer, ignoreCase = true)) return null
        return credentials.trim()
    }
}
