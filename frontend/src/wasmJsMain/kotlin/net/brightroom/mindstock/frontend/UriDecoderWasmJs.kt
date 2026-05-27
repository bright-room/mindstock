package net.brightroom.mindstock.frontend

@JsFun("(s) => decodeURIComponent(s)")
private external fun decodeUriComponentImpl(s: String): String

internal actual fun decodeUriComponent(s: String): String = decodeUriComponentImpl(s)
