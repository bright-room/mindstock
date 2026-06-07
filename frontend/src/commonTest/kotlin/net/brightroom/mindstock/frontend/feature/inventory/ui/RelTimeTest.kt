package net.brightroom.mindstock.frontend.feature.inventory.ui

import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt
import net.brightroom.mindstock.extensions.kotlinx.datetime.JST
import kotlin.test.Test

class RelTimeTest {
    private val now = LocalDateTime(2026, 6, 8, 12, 0).toInstant(TimeZone.JST)

    private fun at(
        month: Int,
        day: Int,
        hour: Int,
        minute: Int = 0,
    ) = OccurredAt(LocalDateTime(2026, month, day, hour, minute))

    @Test
    fun within_an_hour_is_just_now() {
        relTimeOf(at(6, 8, 11, 30), now) shouldBe RelTime.JustNow
    }

    @Test
    fun within_a_day_is_hours_ago() {
        relTimeOf(at(6, 8, 7, 0), now) shouldBe RelTime.HoursAgo(5)
    }

    @Test
    fun within_a_week_is_days_ago() {
        relTimeOf(at(6, 6, 12, 0), now) shouldBe RelTime.DaysAgo(2)
    }

    @Test
    fun older_than_a_week_is_a_date() {
        relTimeOf(at(5, 25, 12, 0), now) shouldBe RelTime.OnDate(5, 25)
    }
}
