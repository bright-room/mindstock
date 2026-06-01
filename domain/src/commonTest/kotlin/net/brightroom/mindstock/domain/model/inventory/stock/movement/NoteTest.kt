package net.brightroom.mindstock.domain.model.inventory.stock.movement

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class NoteTest {
    @Test
    fun 空文字のメモを受理する() {
        Note("").invoke() shouldBe ""
    }

    @Test
    fun 前後の空白をトリムする() {
        Note("  まとめ買い  ").invoke() shouldBe "まとめ買い"
    }

    @Test
    fun 最大長を超えるメモは拒否する() {
        shouldThrow<IllegalArgumentException> { Note("あ".repeat(256)) }
    }
}
