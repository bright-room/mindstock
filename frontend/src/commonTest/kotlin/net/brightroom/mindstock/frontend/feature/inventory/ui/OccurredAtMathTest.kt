package net.brightroom.mindstock.frontend.feature.inventory.ui

import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test

class OccurredAtMathTest {
    @Test
    fun combines_selected_date_with_current_time() {
        val now = LocalDateTime(2026, 6, 8, 14, 30, 15)
        val picked = LocalDate(2026, 6, 6)
        occurredAtOf(picked, now)() shouldBe LocalDateTime(2026, 6, 6, 14, 30, 15)
    }

    @Test
    fun today_keeps_full_now() {
        val now = LocalDateTime(2026, 6, 8, 14, 30, 15)
        occurredAtOf(LocalDate(2026, 6, 8), now)() shouldBe now
    }
}
