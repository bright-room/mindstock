package net.brightroom.mindstock.frontend.feature.activity

import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.activity_day_n_days_ago
import mindstock.frontend.generated.resources.activity_day_today
import mindstock.frontend.generated.resources.activity_day_yesterday
import kotlin.test.Test

class ActivityGroupingTest {
    private val today = LocalDate(2026, 6, 7)

    @Test
    fun today_label() {
        val d = LocalDateTime(2026, 6, 7, 9, 30)
        dayLabel(d, today) shouldBe DayLabel.Resource(Res.string.activity_day_today)
    }

    @Test
    fun yesterday_label() {
        val d = LocalDateTime(2026, 6, 6, 23, 0)
        dayLabel(d, today) shouldBe DayLabel.Resource(Res.string.activity_day_yesterday)
    }

    @Test
    fun n_days_ago_label() {
        val d = LocalDateTime(2026, 6, 4, 12, 0)
        dayLabel(d, today) shouldBe DayLabel.NDaysAgo(Res.string.activity_day_n_days_ago, 3)
    }

    @Test
    fun old_date_falls_back_to_iso() {
        val d = LocalDateTime(2026, 5, 1, 12, 0)
        dayLabel(d, today) shouldBe DayLabel.Date("2026-05-01")
    }

    @Test
    fun hm_pads_minutes() {
        hm(LocalDateTime(2026, 6, 7, 9, 5)) shouldBe "9:05"
    }
}
