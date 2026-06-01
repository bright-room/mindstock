package net.brightroom.mindstock.domain.model.inventory.product.setting

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ProductUnitTest {
    @Test
    fun 前後の空白をトリムして受理する() {
        ProductUnit("  個  ").invoke() shouldBe "個"
    }

    @Test
    fun 空白のみは拒否する() {
        shouldThrow<IllegalArgumentException> { ProductUnit("  ") }
    }

    @Test
    fun 最大長を超える単位名は拒否する() {
        shouldThrow<IllegalArgumentException> { ProductUnit("あ".repeat(11)) }
    }
}
