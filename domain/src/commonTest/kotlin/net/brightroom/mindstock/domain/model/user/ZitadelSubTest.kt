package net.brightroom.mindstock.domain.model.user

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.exception.DomainException
import kotlin.test.Test

class ZitadelSubTest {
    @Test
    fun `accepts non-blank`() {
        ZitadelSub("abc-123").toString() shouldBe "abc-123"
    }

    @Test
    fun `rejects blank`() {
        shouldThrow<DomainException.ZitadelSubBlank> { ZitadelSub("") }
        shouldThrow<DomainException.ZitadelSubBlank> { ZitadelSub("   ") }
    }
}
