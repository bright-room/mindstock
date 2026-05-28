package net.brightroom.mindstock.frontend.auth

import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TokenStoreTest {
    @AfterTest fun cleanup() {
        TokenStore.clear()
    }

    @Test
    fun save_and_load_roundtrip() {
        val t = Tokens("a", "r", "i", Instant.fromEpochSeconds(2_000_000))
        TokenStore.save(t)
        assertEquals(t, TokenStore.load())
    }

    @Test
    fun load_returns_null_when_empty() {
        TokenStore.clear()
        assertNull(TokenStore.load())
    }

    @Test
    fun clear_removes_value() {
        TokenStore.save(Tokens("a", "r", "i", Instant.fromEpochSeconds(2_000_000)))
        TokenStore.clear()
        assertNull(TokenStore.load())
    }
}
