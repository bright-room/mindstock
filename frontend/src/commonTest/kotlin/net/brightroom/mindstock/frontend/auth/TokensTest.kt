package net.brightroom.mindstock.frontend.auth

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TokensTest {
    @Test
    fun willExpireWithin_true_when_expiry_is_inside_window() {
        val now = Instant.fromEpochSeconds(1_000_000)
        val t = Tokens("a", "r", "i", expiresAt = Instant.fromEpochSeconds(1_000_030))
        assertTrue(t.willExpireWithin(60, now))
    }

    @Test
    fun willExpireWithin_false_when_expiry_is_outside_window() {
        val now = Instant.fromEpochSeconds(1_000_000)
        val t = Tokens("a", "r", "i", expiresAt = Instant.fromEpochSeconds(1_000_120))
        assertFalse(t.willExpireWithin(60, now))
    }

    @Test
    fun fromTokenResponse_computes_expiresAt() {
        val now = Instant.fromEpochSeconds(1_000_000)
        val t = Tokens.fromTokenResponse(
            accessToken = "a", refreshToken = "r", idToken = "i", expiresInSeconds = 3600, now = now,
        )
        assertEquals(Instant.fromEpochSeconds(1_003_600), t.expiresAt)
    }
}
