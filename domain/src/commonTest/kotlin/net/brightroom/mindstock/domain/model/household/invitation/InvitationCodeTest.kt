package net.brightroom.mindstock.domain.model.household.invitation

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class InvitationCodeTest {
    @Test
    fun rejects_wrong_length() {
        shouldThrow<IllegalArgumentException> { InvitationCode("ABC23") }
    }

    @Test
    fun rejects_ambiguous_chars() {
        shouldThrow<IllegalArgumentException> { InvitationCode("ABC230") } // 0 は除外
        shouldThrow<IllegalArgumentException> { InvitationCode("ABCO23") } // O は除外
    }

    @Test
    fun generate_produces_valid_code() {
        val code = InvitationCode.generate()
        code.invoke().length shouldBe 6
        code.invoke().all { it in InvitationCode.ALPHABET } shouldBe true
    }
}
