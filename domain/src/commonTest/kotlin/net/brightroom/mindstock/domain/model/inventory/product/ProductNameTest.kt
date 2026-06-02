package net.brightroom.mindstock.domain.model.inventory.product

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ProductNameTest {
    @Test
    fun 前後の空白をトリムする() {
        ProductName("  牛乳  ").invoke() shouldBe "牛乳"
    }

    @Test
    fun 空文字は拒否する() {
        shouldThrow<IllegalArgumentException> { ProductName("   ") }
    }

    @Test
    fun 最大長を超えると拒否する() {
        shouldThrow<IllegalArgumentException> { ProductName("あ".repeat(ProductName.MAX_LENGTH + 1)) }
    }

    @Test
    fun 最大長ちょうどは許容する() {
        val name = "あ".repeat(ProductName.MAX_LENGTH)
        ProductName(name).invoke() shouldBe name
    }
}
