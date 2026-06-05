package net.brightroom.mindstock.frontend.auth

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import kotlin.test.Test

class PkceTest {
    @Test
    fun newVerifier_has_requested_length_and_unreserved_charset() {
        val v = Pkce.newVerifier(64)
        v.length shouldBe 64
        v shouldMatch Regex("^[A-Za-z0-9._~-]+$")
    }

    @Test
    fun newVerifier_rejects_out_of_range_length() {
        try {
            Pkce.newVerifier(10)
            throw AssertionError("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }
}
