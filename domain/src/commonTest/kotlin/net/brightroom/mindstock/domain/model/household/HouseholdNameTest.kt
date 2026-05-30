package net.brightroom.mindstock.domain.model.household

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class HouseholdNameTest {
    @Test
    fun `値を保持し toString と invoke で取り出せる`() {
        val name = HouseholdName("我が家")
        name.toString() shouldBe "我が家"
        name() shouldBe "我が家"
    }

    @Test
    fun `空白のみは拒否する`() {
        shouldThrow<IllegalArgumentException> { HouseholdName(" ") }
    }

    @Test
    fun `100 文字超は拒否する`() {
        shouldThrow<IllegalArgumentException> { HouseholdName("あ".repeat(101)) }
    }
}
