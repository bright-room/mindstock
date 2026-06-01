package net.brightroom.mindstock.domain.model.household

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class HouseholdNameTest {
    @Test
    fun trims_and_accepts() {
        HouseholdName("  我が家  ").invoke() shouldBe "我が家"
    }

    @Test
    fun rejects_blank() {
        shouldThrow<IllegalArgumentException> { HouseholdName("  ") }
    }

    @Test
    fun rejects_over_30_chars() {
        shouldThrow<IllegalArgumentException> { HouseholdName("あ".repeat(31)) }
    }
}
