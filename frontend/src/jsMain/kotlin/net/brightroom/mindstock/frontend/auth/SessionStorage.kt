package net.brightroom.mindstock.frontend.auth

import kotlinx.browser.window

internal actual object SessionStorage {
    actual fun get(key: String): String? = window.sessionStorage.getItem(key)

    actual fun set(
        key: String,
        value: String,
    ) {
        window.sessionStorage.setItem(key, value)
    }

    actual fun remove(key: String) {
        window.sessionStorage.removeItem(key)
    }
}
