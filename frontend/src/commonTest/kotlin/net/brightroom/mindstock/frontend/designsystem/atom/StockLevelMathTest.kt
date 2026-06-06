package net.brightroom.mindstock.frontend.designsystem.atom

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class StockLevelMathTest {
    @Test
    fun comfortable_is_at_least_min_times_two() {
        comfortableStock(qty = 0, min = 4) shouldBe 8 // max(8, 7, 0, 1) = 8
    }

    @Test
    fun comfortable_uses_min_plus_three_when_larger() {
        comfortableStock(qty = 0, min = 1) shouldBe 4
    }

    @Test
    fun comfortable_never_below_qty() {
        comfortableStock(qty = 10, min = 1) shouldBe 10
    }

    @Test
    fun fill_fraction_is_qty_over_comfortable_clamped() {
        fillFraction(qty = 2, min = 1).toDouble() shouldBe (0.5 plusOrMinus 0.0001)
        fillFraction(qty = 0, min = 1).toDouble() shouldBe (0.0 plusOrMinus 0.0001)
    }

    @Test
    fun min_marker_fraction_is_min_over_comfortable() {
        minFraction(qty = 0, min = 1).toDouble() shouldBe (0.25 plusOrMinus 0.0001)
    }
}
