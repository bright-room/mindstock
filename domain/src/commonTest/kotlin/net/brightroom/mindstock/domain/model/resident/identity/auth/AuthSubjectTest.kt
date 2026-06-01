package net.brightroom.mindstock.domain.model.resident.identity.auth

import io.kotest.assertions.throwables.shouldThrow
import kotlin.test.Test

class AuthSubjectTest {
    @Test
    fun 空白のsubjectは拒否する() {
        shouldThrow<IllegalArgumentException> { AuthSubject(" ") }
    }
}
