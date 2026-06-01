package net.brightroom.mindstock.domain.model.household

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class HouseholdNameTest {
    @Test
    fun 前後の空白をトリムして受理する() {
        HouseholdName("  我が家  ").invoke() shouldBe "我が家"
    }

    @Test
    fun 空文字は拒否する() {
        shouldThrow<IllegalArgumentException> { HouseholdName("  ") }
    }

    @Test
    fun 最大長を超える世帯名は拒否する() {
        shouldThrow<IllegalArgumentException> { HouseholdName("あ".repeat(31)) }
    }
}
