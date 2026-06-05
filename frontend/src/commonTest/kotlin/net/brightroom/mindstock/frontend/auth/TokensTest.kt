package net.brightroom.mindstock.frontend.auth

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class TokensTest {
    private val epoch = Instant.fromEpochSeconds(1_000_000)

    @Test
    fun fromTokenResponse_expiresAt_is_now_plus_expiresIn() {
        val t = Tokens.fromTokenResponse("a", "r", "i", expiresInSeconds = 3600, now = epoch)
        t.expiresAt shouldBe epoch + 3600.seconds
    }

    @Test
    fun willExpireWithin_true_when_within_window() {
        val t = Tokens("a", "r", "i", expiresAt = epoch + 30.seconds)
        t.willExpireWithin(60, now = epoch) shouldBe true
        t.willExpireWithin(10, now = epoch) shouldBe false
    }
}
