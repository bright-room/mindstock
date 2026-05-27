package net.brightroom.mindstock.frontend.auth

import kotlin.random.Random

object Pkce {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

    fun newVerifier(length: Int = 64): String {
        require(length in 43..128) { "verifier length must be 43..128, was $length" }
        val sb = StringBuilder(length)
        repeat(length) { sb.append(ALPHABET[Random.nextInt(ALPHABET.length)]) }
        return sb.toString()
    }

    suspend fun challenge(verifier: String): String =
        base64UrlNoPad(sha256(verifier.encodeToByteArray()))
}

internal expect suspend fun sha256(bytes: ByteArray): ByteArray
internal expect fun base64UrlNoPad(bytes: ByteArray): String
