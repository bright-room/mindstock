package net.brightroom.mindstock.domain.model.stock

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlin.test.Test

class QuantityTest {
    @Test
    fun `accepts positive integer`() {
        Quantity(1).toString() shouldBe "1"
        Quantity(42).toString() shouldBe "42"
    }

    @Test
    fun `rejects zero`() {
        shouldThrow<IllegalArgumentException> { Quantity(0) }
    }

    @Test
    fun `rejects negative`() {
        shouldThrow<IllegalArgumentException> { Quantity(-1) }
    }

    @Test
    fun `serializes to plain integer JSON`() {
        Json.encodeToString(Quantity.serializer(), Quantity(5)) shouldBe "5"
    }

    @Test
    fun `deserializes from plain integer JSON`() {
        Json.decodeFromString(Quantity.serializer(), "5") shouldBe Quantity(5)
    }
}
