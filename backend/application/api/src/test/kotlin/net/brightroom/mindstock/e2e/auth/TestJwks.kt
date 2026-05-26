package net.brightroom.mindstock.e2e.auth

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.util.Base64

/**
 * [TestKeyPair] の公開鍵を JWKS (RFC 7517) JSON 文字列にする。
 * `kid` は [TestKeyPair.KID] と一致。アルゴリズムは RS256。
 */
object TestJwks {
    fun asJsonString(): String {
        val pub = TestKeyPair.publicKey
        val n = Base64.getUrlEncoder().withoutPadding().encodeToString(stripLeadingZero(pub.modulus.toByteArray()))
        val e = Base64.getUrlEncoder().withoutPadding().encodeToString(stripLeadingZero(pub.publicExponent.toByteArray()))
        val keys =
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("kty", JsonPrimitive("RSA"))
                        put("kid", JsonPrimitive(TestKeyPair.KID))
                        put("alg", JsonPrimitive("RS256"))
                        put("use", JsonPrimitive("sig"))
                        put("n", JsonPrimitive(n))
                        put("e", JsonPrimitive(e))
                    },
                )
            }
        return JsonObject(mapOf("keys" to keys)).toString()
    }

    // BigInteger.toByteArray() は符号付きで先頭 0x00 が付くことがある。JWK は magnitude のみ。
    private fun stripLeadingZero(bytes: ByteArray): ByteArray =
        if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
}
