package net.brightroom.mindstock.domain.model.stock.movement

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class QuantityTest {
    @Test
    fun `accepts positive integer`() {
        Quantity(1).toString() shouldBe "1"
        Quantity(42).toString() shouldBe "42"
    }

    @Test
    fun `rejects zero`() {
        shouldThrow<IllegalArgumentException> { Quantity(0) }
    }

    @Test
    fun `rejects negative`() {
        shouldThrow<IllegalArgumentException> { Quantity(-1) }
    }
}
