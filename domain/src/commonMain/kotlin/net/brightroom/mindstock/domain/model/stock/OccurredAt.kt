package net.brightroom.mindstock.domain.model.stock

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 在庫イベント発生時刻。未来日は許容しない。
 *
 * 生成時とデシリアライズ時の双方で [Clock.System.now] と比較し、`value > now`
 * の場合 [IllegalArgumentException] を throw。
 */
@Serializable
@JvmInline
value class OccurredAt(
    private val value: Instant,
) {
    init {
        require(value <= Clock.System.now()) { "occurredAt $value must be <= now" }
    }

    override fun toString(): String = value.toString()

    operator fun invoke(): Instant = value
}
