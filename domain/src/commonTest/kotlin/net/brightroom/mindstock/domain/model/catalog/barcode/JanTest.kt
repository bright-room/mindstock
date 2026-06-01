package net.brightroom.mindstock.domain.model.catalog.barcode

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class JanTest {
    @Test
    fun 正しいEAN13を受理する() {
        Jan("4901234567894").invoke() shouldBe "4901234567894"
    }

    @Test
    fun チェックデジットが誤っていれば拒否する() {
        shouldThrow<IllegalArgumentException> { Jan("4901234567890") }
    }

    @Test
    fun 桁数が13でなければ拒否する() {
        shouldThrow<IllegalArgumentException> { Jan("490123456789") }
    }

    @Test
    fun 数字以外の文字は拒否する() {
        shouldThrow<IllegalArgumentException> { Jan("49012345678AB") }
    }
}
