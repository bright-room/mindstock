package net.brightroom.mindstock.domain.model.inventory.stock.movement

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class NoteTest {
    @Test
    fun allows_empty_note() {
        Note("").invoke() shouldBe ""
    }

    @Test
    fun trims_surrounding_whitespace() {
        Note("  まとめ買い  ").invoke() shouldBe "まとめ買い"
    }

    @Test
    fun rejects_over_255_chars() {
        shouldThrow<IllegalArgumentException> { Note("あ".repeat(256)) }
    }
}
