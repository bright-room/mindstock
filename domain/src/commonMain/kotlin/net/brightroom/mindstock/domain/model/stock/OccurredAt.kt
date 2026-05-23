package net.brightroom.mindstock.domain.model.stock

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.exception.DomainException
import kotlin.time.Instant

/**
 * 在庫イベント発生時刻。未来日は許容しない(now を比較対象として渡す)。
 *
 * `value > now` の場合 [DomainException.OccurredAtInFuture] を throw。
 * `value <= now` のときに有効。
 *
 * - primary constructor `(value)` は無検証(シリアライズ復元用)
 * - secondary constructor `(value, now)` で `value > now` をガード
 * - `data class` により構造的等価性を持つ(同じ Instant 同士は equals/hashCode で等価)
 */
@Serializable
data class OccurredAt(
    private val value: Instant,
) {
    constructor(value: Instant, now: Instant) : this(value) {
        if (value > now) throw DomainException.OccurredAtInFuture(value, now)
    }

    override fun toString(): String = value.toString()

    internal operator fun invoke(): Instant = value
}
