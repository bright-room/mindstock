package net.brightroom.mindstock.domain.model.inventory.stock.movement

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ReasonTest {
    @Test
    fun trims_and_accepts() {
        Reason("  数え直し  ").invoke() shouldBe "数え直し"
    }

    @Test
    fun rejects_blank() {
        shouldThrow<IllegalArgumentException> { Reason("  ") }
    }

    @Test
    fun rejects_over_255_chars() {
        shouldThrow<IllegalArgumentException> { Reason("あ".repeat(256)) }
    }
}
