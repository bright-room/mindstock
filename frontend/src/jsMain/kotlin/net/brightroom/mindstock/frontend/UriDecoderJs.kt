package net.brightroom.mindstock.frontend

internal actual fun decodeUriComponent(s: String): String = js("decodeURIComponent(s)")
