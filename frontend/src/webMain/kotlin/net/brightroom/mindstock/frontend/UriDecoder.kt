package net.brightroom.mindstock.frontend

/** Decodes a percent-encoded URI component (delegates to the platform's `decodeURIComponent`). */
internal expect fun decodeUriComponent(s: String): String
