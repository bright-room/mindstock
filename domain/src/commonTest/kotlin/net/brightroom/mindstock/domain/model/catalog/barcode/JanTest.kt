package net.brightroom.mindstock.domain.model.catalog.barcode

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class JanTest {
    @Test
    fun accepts_valid_ean13() {
        Jan("4901234567894").invoke() shouldBe "4901234567894"
    }

    @Test
    fun rejects_wrong_check_digit() {
        shouldThrow<IllegalArgumentException> { Jan("4901234567890") }
    }

    @Test
    fun rejects_non_13_length() {
        shouldThrow<IllegalArgumentException> { Jan("490123456789") }
    }

    @Test
    fun rejects_non_digits() {
        shouldThrow<IllegalArgumentException> { Jan("49012345678AB") }
    }
}
