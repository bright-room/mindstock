package net.brightroom.mindstock.domain.model.user.auth

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.exception.DomainException
import kotlin.test.Test

class AuthSubjectTest {
    @Test
    fun `accepts non-blank`() {
        AuthSubject("abc-123").toString() shouldBe "abc-123"
    }

    @Test
    fun `rejects blank`() {
        shouldThrow<DomainException.AuthSubjectBlank> { AuthSubject("") }
        shouldThrow<DomainException.AuthSubjectBlank> { AuthSubject("   ") }
    }
}
