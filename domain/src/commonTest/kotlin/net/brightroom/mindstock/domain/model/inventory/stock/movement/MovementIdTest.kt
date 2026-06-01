package net.brightroom.mindstock.domain.model.inventory.stock.movement

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class MovementIdTest {
    @Test
    fun accepts_zero_and_positive() {
        MovementId(0).invoke() shouldBe 0L
        MovementId(42).invoke() shouldBe 42L
    }

    @Test
    fun rejects_negative() {
        shouldThrow<IllegalArgumentException> { MovementId(-1) }
    }
}
