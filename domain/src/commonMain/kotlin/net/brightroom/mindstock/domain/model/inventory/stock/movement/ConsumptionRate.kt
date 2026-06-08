package net.brightroom.mindstock.domain.model.inventory.stock.movement

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * 1 日あたりの消費数量(単位/日)。消費履歴から推定したペースで、非負。0.0 は「予測不可(消費実績なし)」を表す。
 */
@Serializable
@JvmInline
value class ConsumptionRate(
    private val value: Double,
) {
    init {
        require(value >= 0) { "ConsumptionRate must be non-negative: $value" }
    }

    operator fun invoke(): Double = value

    override fun toString(): String = value.toString()
}
