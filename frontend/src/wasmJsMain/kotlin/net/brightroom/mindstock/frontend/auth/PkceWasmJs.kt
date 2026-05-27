package net.brightroom.mindstock.frontend.auth

import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get

@JsFun("(n) => { var a = new Uint8Array(n); globalThis.crypto.getRandomValues(a); return a; }")
private external fun createRandomBytesWasm(n: Int): Uint8Array

internal actual fun secureRandomBytes(n: Int): ByteArray {
    val arr = createRandomBytesWasm(n)
    return ByteArray(n) { arr[it].toByte() }
}

internal actual suspend fun sha256(bytes: ByteArray): ByteArray {
    val uint8 = newUint8Array(bytes.size)
    for (i in bytes.indices) setUint8(uint8, i, bytes[i].toInt() and 0xFF)
    val digest: ArrayBuffer = subtleDigest(uint8).await()!!.unsafeCast()
    val view = Uint8Array(digest)
    return ByteArray(view.length) { view[it].toByte() }
}

internal actual fun base64UrlNoPad(bytes: ByteArray): String {
    val sb = StringBuilder(bytes.size)
    for (b in bytes) sb.append((b.toInt() and 0xFF).toChar())
    return btoa(sb.toString()).replace('+', '-').replace('/', '_').trimEnd('=')
}

@JsFun("(n) => new Uint8Array(n)")
private external fun newUint8Array(size: Int): Uint8Array

@JsFun("(arr, i, v) => { arr[i] = v; }")
private external fun setUint8(arr: Uint8Array, index: Int, value: Int)

@JsFun("(arr) => globalThis.crypto.subtle.digest('SHA-256', arr)")
private external fun subtleDigest(data: Uint8Array): kotlin.js.Promise<JsAny?>

@JsFun("(s) => globalThis.btoa(s)")
private external fun btoa(s: String): String
