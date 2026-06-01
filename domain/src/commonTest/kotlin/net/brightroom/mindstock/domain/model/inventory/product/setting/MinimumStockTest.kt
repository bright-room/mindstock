package net.brightroom.mindstock.domain.model.inventory.product.setting

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class MinimumStockTest {
    @Test
    fun rejects_negative() {
        shouldThrow<IllegalArgumentException> { MinimumStock(-1) }
    }

    @Test
    fun isBelow_is_true_when_current_at_or_under_minimum() {
        MinimumStock(3).isBelow(3) shouldBe true
        MinimumStock(3).isBelow(2) shouldBe true
    }

    @Test
    fun isBelow_is_false_when_current_above_minimum() {
        MinimumStock(3).isBelow(4) shouldBe false
    }

    @Test
    fun shortage_is_gap_to_minimum_clamped_at_zero() {
        MinimumStock(3).shortage(1) shouldBe 2
        MinimumStock(3).shortage(5) shouldBe 0
    }
}
