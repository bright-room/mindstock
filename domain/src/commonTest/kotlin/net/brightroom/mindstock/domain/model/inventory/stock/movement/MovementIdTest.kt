package net.brightroom.mindstock.domain.model.inventory.stock.movement

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class MovementIdTest {
    @Test
    fun ゼロと正の値を受理する() {
        MovementId(0).invoke() shouldBe 0L
        MovementId(42).invoke() shouldBe 42L
    }

    @Test
    fun 負の値は拒否する() {
        shouldThrow<IllegalArgumentException> { MovementId(-1) }
    }
}
