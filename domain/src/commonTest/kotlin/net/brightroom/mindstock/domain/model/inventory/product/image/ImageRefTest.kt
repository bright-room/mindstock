package net.brightroom.mindstock.domain.model.inventory.product.image

import io.kotest.assertions.throwables.shouldThrow
import kotlin.test.Test

class ImageRefTest {
    @Test
    fun 空白のみは拒否する() {
        shouldThrow<IllegalArgumentException> { ImageRef("  ") }
    }
}
