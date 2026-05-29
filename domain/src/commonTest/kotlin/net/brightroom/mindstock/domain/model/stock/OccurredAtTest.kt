package net.brightroom.mindstock.domain.model.stock

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class OccurredAtTest {
    @Test
    fun `accepts past`() {
        val past = Instant.parse("2026-05-22T10:00:00Z")
        OccurredAt(past).toString() shouldBe past.toString()
    }

    @Test
    fun `accepts approximately now`() {
        val now = Clock.System.now()
        OccurredAt(now).toString() shouldBe now.toString()
    }

    @Test
    fun `rejects future`() {
        val future = Clock.System.now().plus(1.days)
        shouldThrow<IllegalArgumentException> { OccurredAt(future) }
    }

    @Test
    fun `rejects near-future`() {
        val nearFuture = Clock.System.now().plus(60.seconds)
        shouldThrow<IllegalArgumentException> { OccurredAt(nearFuture) }
    }
}
