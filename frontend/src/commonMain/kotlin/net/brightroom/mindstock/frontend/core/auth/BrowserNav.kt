package net.brightroom.mindstock.frontend.core.auth

/** ブラウザ遷移とコールバック取得を抽象化(platform 依存)。 */
internal expect object BrowserNav {
    fun currentPath(): String

    fun currentQueryParam(name: String): String?

    fun assign(url: String)

    fun replace(url: String)
}
