package net.brightroom.mindstock.domain.model.inventory.stock

import kotlin.jvm.JvmInline

/**
 * 消費予測の結果。nullable 戻り値原則禁止に従い、「予測不可」を null でなく型で表す。
 *
 * 算出のみの read-model で永続化・通信はしないため `@Serializable` は付けない
 * (value class を sealed variant にすると polymorphic serialize が壊れるため、付けるなら DaysRemaining を
 *  data class に戻す必要がある。現状は wire 型でないので value class のまま非シリアライズとする)。
 */
sealed interface ConsumptionForecast {
    /** 予測不可。消費実績が無い／現在在庫が 0 以下。 */
    data object Unknown : ConsumptionForecast

    /** 現在のペースであと約 days 日で在庫が尽きる見込み。残日数 = 現在の在庫数量 ÷ 1日あたり消費ペース を四捨五入(>= 0)。 */
    @JvmInline
    value class DaysRemaining(
        private val value: Int,
    ) : ConsumptionForecast {
        init {
            require(value >= 0) { "DaysRemaining must be non-negative: $value" }
        }

        operator fun invoke(): Int = value

        override fun toString(): String = value.toString()
    }
}
