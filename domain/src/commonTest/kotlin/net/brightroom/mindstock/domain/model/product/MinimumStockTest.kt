package net.brightroom.mindstock.domain.model.product

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class MinimumStockTest {
    @Test
    fun `Set rejects negative`() {
        shouldThrow<IllegalArgumentException> { MinimumStock.Set(-1) }
    }

    @Test
    fun `Set accepts zero`() {
        MinimumStock.Set(0)() shouldBe 0
    }

    @Test
    fun `Set accepts positive`() {
        MinimumStock.Set(10)() shouldBe 10
    }

    @Test
    fun `Set isBelow returns true when quantity is less than value`() {
        MinimumStock.Set(5).isBelow(3).shouldBeTrue()
    }

    @Test
    fun `Set isBelow returns false when quantity equals value`() {
        MinimumStock.Set(5).isBelow(5).shouldBeFalse()
    }

    @Test
    fun `Set isBelow returns false when quantity exceeds value`() {
        MinimumStock.Set(5).isBelow(10).shouldBeFalse()
    }

    @Test
    fun `Set shortage returns deficit when below minimum`() {
        MinimumStock.Set(5).shortage(3) shouldBe 2
    }

    @Test
    fun `Set shortage returns zero when quantity meets minimum`() {
        MinimumStock.Set(5).shortage(5) shouldBe 0
    }

    @Test
    fun `Set shortage returns zero when quantity exceeds minimum`() {
        MinimumStock.Set(5).shortage(10) shouldBe 0
    }

    @Test
    fun `NotSet isBelow always returns false`() {
        MinimumStock.NotSet.isBelow(0).shouldBeFalse()
        MinimumStock.NotSet.isBelow(100).shouldBeFalse()
    }

    @Test
    fun `NotSet shortage always returns zero`() {
        MinimumStock.NotSet.shortage(0) shouldBe 0
        MinimumStock.NotSet.shortage(100) shouldBe 0
    }
}
