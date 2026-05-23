package net.brightroom.mindstock.domain.model.stock

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant

class CorrectedAtTest {
    @Test
    fun `wraps an Instant`() {
        val instant = Instant.parse("2026-05-24T10:00:00Z")
        CorrectedAt(instant).toString() shouldBe "2026-05-24T10:00:00Z"
    }
}
