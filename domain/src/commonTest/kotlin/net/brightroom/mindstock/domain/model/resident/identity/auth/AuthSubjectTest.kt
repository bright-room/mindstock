package net.brightroom.mindstock.domain.model.resident.identity.auth

import io.kotest.assertions.throwables.shouldThrow
import kotlin.test.Test

class AuthSubjectTest {
    @Test
    fun rejects_blank_subject() {
        shouldThrow<IllegalArgumentException> { AuthSubject(" ") }
    }
}
