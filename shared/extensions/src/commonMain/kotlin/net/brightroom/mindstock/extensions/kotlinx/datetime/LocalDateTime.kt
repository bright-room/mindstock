@file:OptIn(ExperimentalTime::class)

package net.brightroom._extensions.kotlinx.datetime

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

fun LocalDateTime.Companion.now(timeZone: TimeZone = TimeZone.JST): LocalDateTime {
    val instant = Clock.System.now()
    return instant.toLocalDateTime(timeZone)
}
