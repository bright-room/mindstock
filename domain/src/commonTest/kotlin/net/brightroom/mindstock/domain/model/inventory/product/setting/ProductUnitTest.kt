package net.brightroom.mindstock.domain.model.inventory.product.setting

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ProductUnitTest {
    @Test
    fun trims_and_accepts() {
        ProductUnit("  個  ").invoke() shouldBe "個"
    }

    @Test
    fun rejects_blank() {
        shouldThrow<IllegalArgumentException> { ProductUnit("  ") }
    }

    @Test
    fun rejects_over_10_chars() {
        shouldThrow<IllegalArgumentException> { ProductUnit("あ".repeat(11)) }
    }
}
