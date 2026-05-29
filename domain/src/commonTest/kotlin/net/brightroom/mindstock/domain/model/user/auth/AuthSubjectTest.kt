package net.brightroom.mindstock.domain.model.user.auth

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class AuthSubjectTest {
    @Test
    fun `accepts non-blank`() {
        AuthSubject("abc-123").toString() shouldBe "abc-123"
    }

    @Test
    fun `rejects blank`() {
        shouldThrow<IllegalArgumentException> { AuthSubject("") }
        shouldThrow<IllegalArgumentException> { AuthSubject("   ") }
    }
}
