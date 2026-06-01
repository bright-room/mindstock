package net.brightroom.mindstock.domain.model.household.invitation

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class InvitationCodeTest {
    @Test
    fun 桁数が不正なコードは拒否する() {
        shouldThrow<IllegalArgumentException> { InvitationCode("ABC23") }
    }

    @Test
    fun 曖昧な文字を含むコードは拒否する() {
        shouldThrow<IllegalArgumentException> { InvitationCode("ABC230") } // 0 は除外
        shouldThrow<IllegalArgumentException> { InvitationCode("ABCO23") } // O は除外
    }

    @Test
    fun 採番したコードは妥当である() {
        val code = InvitationCode.generate()
        code.invoke().length shouldBe 6
        code.invoke().all { it in InvitationCode.ALPHABET } shouldBe true
    }
}
