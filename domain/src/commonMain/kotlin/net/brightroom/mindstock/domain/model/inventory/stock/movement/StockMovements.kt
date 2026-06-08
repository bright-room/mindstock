package net.brightroom.mindstock.domain.model.inventory.stock.movement

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.inventory.quantity.NetQuantity
import net.brightroom.mindstock.domain.model.inventory.stock.EvaluatedTime
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

    /**
     * 全 movement を畳み込んだ現在の正味在庫数量。
     * 補充(Replenishment)を加算・消費(Consumption)を減算し、訂正(Correction)は対象 movement の数量を
     * 最新の訂正値で上書きしてから集計する(訂正自体は増減を持たない)。
     * 値は 0 や(訂正途中の不整合では)負にもなり得るため Quantity(>0)ではなく NetQuantity を返す。
     */
    fun netQuantity(): NetQuantity {
        val latestCorrection = latestCorrectionByTarget()
        val net =
            list.sumOf { movement ->
                when (movement) {
                    is Replenishment -> effectiveQuantity(movement, latestCorrection)
                    is Consumption -> -effectiveQuantity(movement, latestCorrection)
                    is Correction -> 0
                }
            }
        return NetQuantity(net)
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
     * 消費履歴から推定した「1 日あたりの消費数量」(単位/日)。
     *
     * 何を元に: この movement 群の消費(Consumption、訂正反映後の実効数量)と各々の occurredAt。補充は数えない。
     * 基準時刻 asOf からの相対で「直近」を判定する(frontend は now-JST、テストは固定値を渡す)。
     * どう算出: トレーリング窓(直近 [FORECAST_WINDOW_DAYS] 日)に消費があり観測期間が窓を満たすならその窓の平均、
     * そうでなければ(履歴が浅い/直近窓に消費が無い)全履歴平均(最初の補充・消費→asOf を span とする)に fallback。
     * 何を返す: 消費ペース [ConsumptionRate](単位/日)。消費実績が無ければ 0.0(= 予測不可)。
     */
    fun consumptionRatePerDay(asOf: EvaluatedTime): ConsumptionRate {
        if (list.isEmpty()) return ConsumptionRate(0.0)
        val corrections = latestCorrectionByTarget()
        val consumptions =
            list
                .filterIsInstance<Consumption>()
                .map { it.occurredAt() to effectiveQuantity(it, corrections) }
        val totalConsumed = consumptions.sumOf { it.second }
        if (totalConsumed == 0) return ConsumptionRate(0.0)

        val firstDate =
            list
                .filter { it is Replenishment || it is Consumption }
                .minOf { it.occurredAt().date }
        val spanDays = maxOf(1, firstDate.daysUntil(asOf().date))
        val windowStart = asOf().date.minus(DatePeriod(days = FORECAST_WINDOW_DAYS))
        val recentConsumed = consumptions.filter { it.first.date >= windowStart }.sumOf { it.second }

        val rate =
            if (spanDays >= FORECAST_WINDOW_DAYS && recentConsumed > 0) {
                recentConsumed.toDouble() / FORECAST_WINDOW_DAYS
            } else {
                totalConsumed.toDouble() / spanDays
            }
        return ConsumptionRate(rate)
    }

    companion object {
        /** トレーリング窓の日数。 */
        const val FORECAST_WINDOW_DAYS = 60
    }
}
