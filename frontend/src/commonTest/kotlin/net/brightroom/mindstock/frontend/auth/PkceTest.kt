package net.brightroom.mindstock.frontend.auth

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PkceTest {
    @Test
    fun verifier_is_43_to_128_chars_in_unreserved_set() {
        val v = Pkce.newVerifier()
        assertTrue(v.length in 43..128, "length=${v.length}")
        val allowed = ('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('-', '.', '_', '~')
        assertTrue(v.all { it in allowed }, "unexpected char in $v")
    }

    @Test
    fun challenge_is_base64url_sha256_of_verifier() =
        runTest {
            val v = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk" // RFC 7636 Appendix B
            val c = Pkce.challenge(v)
            assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", c)
        }
}
