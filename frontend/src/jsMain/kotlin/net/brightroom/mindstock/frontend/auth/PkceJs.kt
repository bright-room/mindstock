package net.brightroom.mindstock.frontend.auth

import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get

internal actual suspend fun sha256(bytes: ByteArray): ByteArray {
    val input = Int8Array(bytes.toTypedArray())
    val digest = subtleDigestJs(input.buffer).await()
    val view = Uint8Array(digest)
    return ByteArray(view.length) { view[it].toByte() }
}

internal actual fun base64UrlNoPad(bytes: ByteArray): String {
    val sb = StringBuilder(bytes.size)
    for (b in bytes) sb.append((b.toInt() and 0xFF).toChar())
    return jsBtoaJs(sb.toString()).replace('+', '-').replace('/', '_').trimEnd('=')
}

private fun subtleDigestJs(data: ArrayBuffer): kotlin.js.Promise<ArrayBuffer> =
    js("globalThis.crypto.subtle.digest('SHA-256', data)")

private fun jsBtoaJs(s: String): String = js("globalThis.btoa(s)")
