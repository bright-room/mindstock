package net.brightroom.mindstock.domain.model.stock

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ReasonTest {
    @Test
    fun `accepts empty`() {
        Reason("").toString() shouldBe ""
    }

    @Test
    fun `accepts arbitrary text`() {
        Reason("数え間違い").toString() shouldBe "数え間違い"
    }
}
