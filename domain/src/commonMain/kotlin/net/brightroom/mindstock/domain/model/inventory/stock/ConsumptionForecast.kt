package net.brightroom.mindstock.domain.model.inventory.stock

import kotlinx.serialization.Serializable

/**
 * 消費予測の結果。nullable 戻り値原則禁止に従い、「予測不可」を null でなく型で表す。
 */
@Serializable
sealed interface ConsumptionForecast {
    /** 予測不可。消費実績が無い／現在在庫が 0 以下。 */
    @Serializable
    data object Unknown : ConsumptionForecast

    /** 現在のペースであと約 days 日で在庫が尽きる見込み。 */
    @Serializable
    data class DaysRemaining(
        val days: Int,
    ) : ConsumptionForecast
}
