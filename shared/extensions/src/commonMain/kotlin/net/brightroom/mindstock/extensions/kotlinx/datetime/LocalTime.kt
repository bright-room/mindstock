package net.brightroom._extensions.kotlinx.datetime

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

fun LocalTime.Companion.now(timeZone: TimeZone = TimeZone.JST): LocalTime {
    val now = LocalDateTime.now(timeZone)
    return now.time
}
