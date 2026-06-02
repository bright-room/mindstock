@file:OptIn(kotlin.time.ExperimentalTime::class)

package net.brightroom.mindstock.domain.model.inventory.stock.movement

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.time.Clock

@Serializable
@JvmInline
value class OccurredAt(
    private val value: LocalDateTime,
) {
    operator fun invoke(): LocalDateTime = value

    override fun toString(): String = value.toString()

    companion object {
        // IANA "Asia/Tokyo" は wasmJs ランタイムに IANA DB が無く例外。JST は DST 無しのため固定オフセット +09:00 と等価。
        fun now(): OccurredAt = OccurredAt(Clock.System.now().toLocalDateTime(TimeZone.of("+09:00")))
    }
}
