package net.brightroom._extensions.kotlinx.datetime

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone

fun LocalDate.Companion.now(timeZone: TimeZone = TimeZone.JST): LocalDate {
    val now = LocalDateTime.now(timeZone)
    return now.date
}
