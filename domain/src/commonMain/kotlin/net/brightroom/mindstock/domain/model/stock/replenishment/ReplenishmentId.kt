package net.brightroom.mindstock.domain.model.stock.replenishment

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.exception.DomainException
import kotlin.jvm.JvmInline

/**
 * 補充イベントの識別子。DB の stock_replenishments.id(BIGINT autoincrement)に対応。
 *
 * 訂正対象の照合に使う(data class equality が id まで含むため、
 * 同じ内容の Replenishment があっても訂正が正しい対象に当たる)。
 * domain ロジックで `a.id == b.id` のような比較は書かない慣習。
 */
@Serializable
@JvmInline
value class ReplenishmentId(
    private val value: Long,
) {
    init {
        if (value < 0) throw DomainException.InvalidIdentity(value)
    }

    override fun toString(): String = value.toString()

    internal operator fun invoke(): Long = value
}
