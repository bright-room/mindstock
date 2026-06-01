package net.brightroom.mindstock.domain.model.resident.profile

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class DisplayNameTest {
    @Test
    fun trims_surrounding_whitespace() {
        DisplayName("  たろう  ").invoke() shouldBe "たろう"
    }

    @Test
    fun rejects_blank() {
        shouldThrow<IllegalArgumentException> { DisplayName("   ") }
    }

    @Test
    fun rejects_over_100_chars() {
        shouldThrow<IllegalArgumentException> { DisplayName("あ".repeat(101)) }
    }
}
