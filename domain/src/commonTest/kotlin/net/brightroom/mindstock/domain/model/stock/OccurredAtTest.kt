package net.brightroom.mindstock.domain.model.stock

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant

class OccurredAtTest {
    private val now = Instant.parse("2026-05-23T10:00:00Z")

    @Test
    fun `accepts past`() {
        val past = Instant.parse("2026-05-22T10:00:00Z")
        OccurredAt(past, now).toString() shouldBe past.toString()
    }

    @Test
    fun `accepts now exactly`() {
        OccurredAt(now, now).toString() shouldBe now.toString()
    }

    @Test
    fun `rejects future`() {
        val future = Instant.parse("2026-05-24T10:00:00Z")
        shouldThrow<IllegalArgumentException> { OccurredAt(future, now) }
    }

    @Test
    fun `structural equality holds for same instant`() {
        val instant = Instant.parse("2026-05-22T10:00:00Z")
        OccurredAt(instant, now) shouldBe OccurredAt(instant, now)
    }
}
