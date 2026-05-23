package net.brightroom.mindstock.domain.model.stock

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import net.brightroom.mindstock.domain.exception.DomainException
import kotlin.test.Test

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
        shouldThrow<DomainException.OccurredAtInFuture> { OccurredAt(future, now) }
    }
}
