package net.brightroom.mindstock.domain.model.stock.consumption

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.exception.DomainException
import kotlin.jvm.JvmInline

/**
 * 消費イベントの識別子。DB の stock_consumptions.id(BIGINT autoincrement)に対応。
 *
 * 訂正対象の照合に使う。詳細は [ReplenishmentId] と同様。
 */
@Serializable
@JvmInline
value class ConsumptionId(
    private val value: Long,
) {
    init {
        if (value < 0) throw DomainException.InvalidIdentity(value)
    }

    override fun toString(): String = value.toString()

    internal operator fun invoke(): Long = value
}
