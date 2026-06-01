package net.brightroom.mindstock.domain.model.inventory.quantity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class QuantityTest {
    @Test
    fun accepts_positive() {
        Quantity(1).invoke() shouldBe 1
    }

    @Test
    fun rejects_zero() {
        shouldThrow<IllegalArgumentException> { Quantity(0) }
    }

    @Test
    fun rejects_negative() {
        shouldThrow<IllegalArgumentException> { Quantity(-1) }
    }
}
