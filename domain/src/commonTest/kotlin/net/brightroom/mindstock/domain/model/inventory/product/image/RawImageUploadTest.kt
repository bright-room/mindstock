package net.brightroom.mindstock.domain.model.inventory.product.image

import io.kotest.assertions.throwables.shouldThrow
import kotlin.test.Test

class RawImageUploadTest {
    @Test
    fun `空バイト列は拒否`() {
        shouldThrow<IllegalArgumentException> { RawImageUpload(ByteArray(0)) }
    }
}
