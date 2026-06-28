@file:OptIn(ExperimentalWasmJsInterop::class)

package net.brightroom.mindstock.frontend.core.auth

import kotlinx.browser.window
import kotlin.js.ExperimentalWasmJsInterop

internal actual object BrowserNav {
    actual fun currentPath(): String = window.location.pathname

    actual fun currentQueryParam(name: String): String? = queryMap()[name]

    actual fun assign(url: String) {
        window.location.assign(url)
    }

    actual fun replace(url: String) {
        window.location.replace(url)
    }

    private fun queryMap(): Map<String, String> =
        window.location.search
            .removePrefix("?")
            .split("&")
            .mapNotNull {
                if (it.isBlank()) return@mapNotNull null
                val idx = it.indexOf('=')
                val k = if (idx < 0) it else it.substring(0, idx)
                val v = if (idx < 0) "" else it.substring(idx + 1)
                runCatching { decodeUriComponentWasm(k) to decodeUriComponentWasm(v) }.getOrNull()
            }.toMap()
}

@JsFun("(s) => decodeURIComponent(s)")
private external fun decodeUriComponentWasm(s: String): String
