package net.brightroom.mindstock.domain.model.inventory.product.image

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ImageUrlTest {
    @Test
    fun `空文字は拒否`() {
        shouldThrow<IllegalArgumentException> { ImageUrl("") }
    }

    @Test
    fun `URL 文字列を保持`() {
        ImageUrl("https://x/y").invoke() shouldBe "https://x/y"
    }
}
