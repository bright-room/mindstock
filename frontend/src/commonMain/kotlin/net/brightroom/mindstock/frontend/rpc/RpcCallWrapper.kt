package net.brightroom.mindstock.frontend.rpc

/** RPC 呼び出しが 401 相当だった事を表す例外。 */
class UnauthorizedException(message: String = "401") : RuntimeException(message)

/** refresh しても 401 が続く、または refresh 自体が失敗した。呼び出し元は LoggedOut に遷移すべき。 */
class ReauthRequiredException(message: String = "re-authentication required") : RuntimeException(message)

interface Reauth {
    /** 成功なら true、refresh_token が失効していたら false。 */
    suspend fun refresh(): Boolean
}

/**
 * 401 を 1 度だけ refresh + retry するラッパ。
 *
 * @param isUnauthorized 例外を 401 として扱うかを判定するフック。kotlinx-rpc 側の例外型に
 *   依存させずに済むよう、呼び出し側で判定式を差し込める。
 */
class RpcCallWrapper(
    private val reauth: Reauth,
    private val isUnauthorized: Throwable.() -> Boolean = { this is UnauthorizedException },
) {
    suspend fun <T> call(block: suspend () -> T): T =
        try {
            block()
        } catch (e: Throwable) {
            if (!e.isUnauthorized()) throw e
            val refreshed = reauth.refresh()
            if (!refreshed) throw ReauthRequiredException()
            try {
                block()
            } catch (e2: Throwable) {
                if (e2.isUnauthorized()) throw ReauthRequiredException()
                throw e2
            }
        }
}
