package net.brightroom.mindstock.frontend.auth

object Pkce {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

    fun newVerifier(length: Int = 64): String {
        require(length in 43..128) { "verifier length must be 43..128, was $length" }
        val sb = StringBuilder(length)
        while (sb.length < length) {
            val need = length - sb.length
            val bytes = secureRandomBytes(need * 2)
            for (b in bytes) {
                if (sb.length == length) break
                val u = b.toInt() and 0xFF
                if (u < 198) sb.append(ALPHABET[u % ALPHABET.length])
            }
        }
        return sb.toString()
    }

    suspend fun challenge(verifier: String): String = base64UrlNoPad(sha256(verifier.encodeToByteArray()))
}

internal expect fun secureRandomBytes(n: Int): ByteArray

internal expect suspend fun sha256(bytes: ByteArray): ByteArray

internal expect fun base64UrlNoPad(bytes: ByteArray): String
