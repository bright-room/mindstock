package net.brightroom.mindstock.frontend.core.preference

import kotlinx.browser.window

internal actual object PreferenceStore {
    actual fun get(key: String): String? = window.localStorage.getItem(key)

    actual fun set(
        key: String,
        value: String,
    ) {
        window.localStorage.setItem(key, value)
    }

    actual fun remove(key: String) {
        window.localStorage.removeItem(key)
    }
}
