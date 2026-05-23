package net.brightroom.mindstock.domain.model.stock

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class NoteTest {
    @Test
    fun `accepts empty`() {
        Note("").toString() shouldBe ""
    }

    @Test
    fun `accepts arbitrary text`() {
        Note("Costco で 3 個入り").toString() shouldBe "Costco で 3 個入り"
    }
}
