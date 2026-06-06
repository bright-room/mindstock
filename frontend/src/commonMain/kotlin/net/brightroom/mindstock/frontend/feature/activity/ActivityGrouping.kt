package net.brightroom.mindstock.frontend.feature.activity

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.activity_day_n_days_ago
import mindstock.frontend.generated.resources.activity_day_today
import mindstock.frontend.generated.resources.activity_day_yesterday
import net.brightroom.mindstock.rpc.stock.ActivityEntry
import net.brightroom.mindstock.rpc.stock.ActivityFeed
import org.jetbrains.compose.resources.StringResource

/** 活動行の日ラベル。UI 層で文言解決する。 */
sealed interface DayLabel {
    data class Resource(
        val resource: StringResource,
    ) : DayLabel

    data class NDaysAgo(
        val resource: StringResource,
        val days: Int,
    ) : DayLabel

    data class Date(
        val iso: String,
    ) : DayLabel
}

/** 同一日ラベルでまとめたグループ。entries は occurredAt 降順。 */
data class ActivityGroup(
    val label: DayLabel,
    val entries: List<ActivityEntry>,
)

fun dayLabel(
    occurredAt: LocalDateTime,
    today: LocalDate,
): DayLabel {
    val date = occurredAt.date
    val diff = today.toEpochDays() - date.toEpochDays()
    return when {
        diff <= 0L -> DayLabel.Resource(Res.string.activity_day_today)
        diff == 1L -> DayLabel.Resource(Res.string.activity_day_yesterday)
        diff < 7L -> DayLabel.NDaysAgo(Res.string.activity_day_n_days_ago, diff.toInt())
        else -> DayLabel.Date(date.toString())
    }
}

fun hm(occurredAt: LocalDateTime): String = "${occurredAt.hour}:${occurredAt.minute.toString().padStart(2, '0')}"

/** ActivityFeed を occurredAt 降順に並べ、日ラベルでグループ化する。 */
fun ActivityFeed.groupedByDay(today: LocalDate): List<ActivityGroup> {
    val sorted = list.sortedByDescending { it.movement.occurredAt() }
    val result = mutableListOf<ActivityGroup>()
    for (entry in sorted) {
        val label = dayLabel(entry.movement.occurredAt(), today)
        val last = result.lastOrNull()
        if (last != null && last.label == label) {
            result[result.lastIndex] = last.copy(entries = last.entries + entry)
        } else {
            result.add(ActivityGroup(label, listOf(entry)))
        }
    }
    return result
}
