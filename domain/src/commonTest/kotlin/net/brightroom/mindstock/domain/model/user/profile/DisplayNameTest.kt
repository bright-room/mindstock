package net.brightroom.mindstock.domain.model.user.profile

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class DisplayNameTest {
    @Test
    fun `accepts non-blank within 100 chars`() {
        DisplayName("Alice").toString() shouldBe "Alice"
        DisplayName("x".repeat(100)).toString().length shouldBe 100
    }

    @Test
    fun `rejects blank`() {
        shouldThrow<IllegalArgumentException> { DisplayName("") }
        shouldThrow<IllegalArgumentException> { DisplayName("   ") }
    }

    @Test
    fun `rejects over 100 chars`() {
        shouldThrow<IllegalArgumentException> { DisplayName("x".repeat(101)) }
    }
}
