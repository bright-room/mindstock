package net.brightroom.mindstock.domain.model.product

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class MinimumStockTest {
    @Test
    fun `accepts zero`() {
        MinimumStock(0).toString() shouldBe "0"
    }

    @Test
    fun `accepts positive`() {
        MinimumStock(10).toString() shouldBe "10"
    }

    @Test
    fun `rejects negative`() {
        shouldThrow<IllegalArgumentException> { MinimumStock(-1) }
    }
}
