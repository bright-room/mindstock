package net.brightroom.mindstock.domain.model.inventory.product.image

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class RawImageUploadTest {
    @Test
    fun `空バイト列は拒否`() {
        shouldThrow<IllegalArgumentException> { RawImageUpload(ByteArray(0)) }
    }

    @Test
    fun `非空バイト列はそのまま保持`() {
        val bytes = byteArrayOf(1, 2, 3)
        RawImageUpload(bytes).invoke() shouldBe bytes
    }
}
