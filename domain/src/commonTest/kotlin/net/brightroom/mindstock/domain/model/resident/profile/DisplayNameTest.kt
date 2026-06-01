package net.brightroom.mindstock.domain.model.resident.profile

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class DisplayNameTest {
    @Test
    fun 前後の空白をトリムする() {
        DisplayName("  たろう  ").invoke() shouldBe "たろう"
    }

    @Test
    fun 空文字は拒否する() {
        shouldThrow<IllegalArgumentException> { DisplayName("   ") }
    }

    @Test
    fun 最大長を超える名前は拒否する() {
        shouldThrow<IllegalArgumentException> { DisplayName("あ".repeat(101)) }
    }
}
