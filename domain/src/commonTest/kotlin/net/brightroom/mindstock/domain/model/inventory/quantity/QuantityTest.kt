package net.brightroom.mindstock.domain.model.inventory.quantity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class QuantityTest {
    @Test
    fun 正の値を受理する() {
        Quantity(1).invoke() shouldBe 1
    }

    @Test
    fun ゼロは拒否する() {
        shouldThrow<IllegalArgumentException> { Quantity(0) }
    }

    @Test
    fun 負の値は拒否する() {
        shouldThrow<IllegalArgumentException> { Quantity(-1) }
    }
}
