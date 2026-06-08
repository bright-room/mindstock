package net.brightroom.mindstock.domain.model.inventory.stock.movement

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement.Consumption
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement.Correction
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement.Replenishment

@Serializable
data class StockMovements(
    val list: List<StockMovement>,
) {
    fun size(): Int = list.size

    fun add(movement: StockMovement): StockMovements = StockMovements(list + movement)

    fun hasBaseMovement(id: MovementId): Boolean =
        list.any { (it is Replenishment || it is Consumption) && it.identity == MovementIdentity.Persisted(id) }

    fun netQuantity(): Int {
        val latestCorrection = latestCorrectionByTarget()
        return list.sumOf { movement ->
            when (movement) {
                is Replenishment -> effectiveQuantity(movement, latestCorrection)
                is Consumption -> -effectiveQuantity(movement, latestCorrection)
                is Correction -> 0
            }
        }
    }

    /** 同一 target への訂正のうち最新(occurredAt 最大)を採る。 */
    private fun latestCorrectionByTarget(): Map<MovementId, Correction> =
        list
            .filterIsInstance<Correction>()
            .groupBy { it.target }
            // 同一 target に同 occurredAt の訂正が複数ある場合は list 出現順で最初の最大値を採用(実運用では LocalDateTime(同時刻)衝突は起きない前提)
            .mapValues { (_, corrections) -> corrections.maxBy { it.occurredAt() } }

    private fun effectiveQuantity(
        base: StockMovement,
        latestCorrection: Map<MovementId, Correction>,
    ): Int {
        val id = (base.identity as? MovementIdentity.Persisted)?.id
        val correction = id?.let { latestCorrection[it] }
        return correction?.quantity?.invoke() ?: base.quantity()
    }

    /**
     * 1 日あたりの消費ペース。消費(訂正反映後)が無ければ 0.0。
     * トレーリング窓(直近 FORECAST_WINDOW_DAYS 日)に消費があり履歴が窓を満たすならその窓レート、
     * そうでなければ全履歴平均(最初の movement→asOf を span とする)に fallback する。
     */
    fun consumptionRatePerDay(asOf: LocalDateTime): Double {
        if (list.isEmpty()) return 0.0
        val corrections = latestCorrectionByTarget()
        val consumptions =
            list
                .filterIsInstance<Consumption>()
                .map { it.occurredAt() to effectiveQuantity(it, corrections) }
        val totalConsumed = consumptions.sumOf { it.second }
        if (totalConsumed == 0) return 0.0

        val firstDate = list.minOf { it.occurredAt().date }
        val spanDays = maxOf(1, firstDate.daysUntil(asOf.date))
        val windowStart = asOf.date.minus(DatePeriod(days = FORECAST_WINDOW_DAYS))
        val recentConsumed = consumptions.filter { it.first.date >= windowStart }.sumOf { it.second }

        return if (spanDays >= FORECAST_WINDOW_DAYS && recentConsumed > 0) {
            recentConsumed.toDouble() / FORECAST_WINDOW_DAYS
        } else {
            totalConsumed.toDouble() / spanDays
        }
    }

    companion object {
        /** トレーリング窓の日数。 */
        const val FORECAST_WINDOW_DAYS = 60
    }
}
