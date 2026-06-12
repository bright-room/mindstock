package net.brightroom.mindstock.domain.model.inventory.product.image

import io.kotest.assertions.throwables.shouldThrow
import kotlin.test.Test

class ImageUrlTest {
    @Test
    fun `空文字は拒否`() {
        shouldThrow<IllegalArgumentException> { ImageUrl("") }
    }
}
