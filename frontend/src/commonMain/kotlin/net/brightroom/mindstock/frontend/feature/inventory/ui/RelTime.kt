package net.brightroom.mindstock.frontend.feature.inventory.ui

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt
import net.brightroom.mindstock.extensions.kotlinx.datetime.JST
import kotlin.time.Instant

/** 履歴の相対時刻表示。モック `data.jsx` の `relTime` 準拠。 */
sealed interface RelTime {
    data object JustNow : RelTime

    data class HoursAgo(
        val hours: Int,
    ) : RelTime

    data class DaysAgo(
        val days: Int,
    ) : RelTime

    data class OnDate(
        val month: Int,
        val day: Int,
    ) : RelTime
}

/**
 * [occurredAt] と [now] の差から相対時刻区分を求める(JST 壁掛け時刻基準)。
 * 1時間未満=たった今 / 24時間未満=◯時間前 / 7日未満=◯日前 / それ以上=M/D。
 */
fun relTimeOf(
    occurredAt: OccurredAt,
    now: Instant,
): RelTime {
    val then = occurredAt().toInstant(TimeZone.JST)
    val diff = now - then
    val hours = diff.inWholeHours
    return when {
        hours < 1 -> RelTime.JustNow
        hours < 24 -> RelTime.HoursAgo(hours.toInt())
        diff.inWholeDays < 7 -> RelTime.DaysAgo(diff.inWholeDays.toInt())
        else -> RelTime.OnDate(occurredAt().monthNumber, occurredAt().dayOfMonth)
    }
}
