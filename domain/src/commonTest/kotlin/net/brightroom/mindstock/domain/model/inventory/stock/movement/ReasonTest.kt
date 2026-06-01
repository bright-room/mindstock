package net.brightroom.mindstock.domain.model.inventory.stock.movement

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ReasonTest {
    @Test
    fun 前後の空白をトリムして受理する() {
        Reason("  数え直し  ").invoke() shouldBe "数え直し"
    }

    @Test
    fun 空白のみは拒否する() {
        shouldThrow<IllegalArgumentException> { Reason("  ") }
    }

    @Test
    fun 最大長を超える理由は拒否する() {
        shouldThrow<IllegalArgumentException> { Reason("あ".repeat(256)) }
    }
}
