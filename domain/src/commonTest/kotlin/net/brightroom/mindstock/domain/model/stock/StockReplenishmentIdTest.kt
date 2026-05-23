package net.brightroom.mindstock.domain.model.stock

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.exception.DomainException
import kotlin.test.Test

class StockReplenishmentIdTest {
    @Test
    fun `accepts non-negative`() {
        StockReplenishmentId(0).toString() shouldBe "0"
        StockReplenishmentId(42).toString() shouldBe "42"
    }

    @Test
    fun `rejects negative`() {
        shouldThrow<DomainException.InvalidIdentity> { StockReplenishmentId(-1) }
    }
}
