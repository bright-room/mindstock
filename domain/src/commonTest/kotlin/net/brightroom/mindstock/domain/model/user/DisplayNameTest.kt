package net.brightroom.mindstock.domain.model.user

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.exception.DomainException
import kotlin.test.Test

class DisplayNameTest {
    @Test
    fun `accepts non-blank within 100 chars`() {
        DisplayName("Alice").toString() shouldBe "Alice"
        DisplayName("x".repeat(100)).toString().length shouldBe 100
    }

    @Test
    fun `rejects blank`() {
        shouldThrow<DomainException.DisplayNameBlank> { DisplayName("") }
        shouldThrow<DomainException.DisplayNameBlank> { DisplayName("   ") }
    }

    @Test
    fun `rejects over 100 chars`() {
        shouldThrow<DomainException.DisplayNameTooLong> { DisplayName("x".repeat(101)) }
    }
}
