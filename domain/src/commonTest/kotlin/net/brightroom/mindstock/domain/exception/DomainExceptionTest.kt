package net.brightroom.mindstock.domain.exception

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class DomainExceptionTest {
    @Test
    fun `InvalidQuantity carries value in message`() {
        val ex = DomainException.InvalidQuantity(-3)
        ex.shouldBeInstanceOf<DomainException>()
        ex.value shouldBe -3
        ex.message!! shouldContain "-3"
    }
}
