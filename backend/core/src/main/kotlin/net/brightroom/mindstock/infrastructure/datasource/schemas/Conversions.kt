package net.brightroom.mindstock.infrastructure.datasource.schemas

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone
import java.time.ZoneOffset
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

/**
 * `kotlin.time.Instant` を timestamptz 列にマッピングする。
 * Exposed の `timestampWithTimeZone` は `java.time.OffsetDateTime` を返すため、
 * UTC 固定で `Instant` と相互変換する(Instant は時点なので UTC offset で十分)。
 */
fun Table.instantTz(name: String): Column<Instant> =
    timestampWithTimeZone(name).transform(
        unwrap = { it.toJavaInstant().atOffset(ZoneOffset.UTC) },
        wrap = { it.toInstant().toKotlinInstant() },
    )
