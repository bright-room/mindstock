package net.brightroom.mindstock.domain.model.stock

import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.consumption.Consumption
import net.brightroom.mindstock.domain.model.stock.consumption.ConsumptionCorrection
import net.brightroom.mindstock.domain.model.stock.consumption.Consumptions
import net.brightroom.mindstock.domain.model.stock.replenishment.Replenishment
import net.brightroom.mindstock.domain.model.stock.replenishment.ReplenishmentCorrection
import net.brightroom.mindstock.domain.model.stock.replenishment.Replenishments

/**
 * 在庫状態。
 *
 * 1 つの Product に対する補充・消費・訂正の集約から、現在数量・買い物リスト要否を計算する。
 */
class Stock(
    val product: Product,
    val replenishments: Replenishments,
    val consumptions: Consumptions,
    private val replenishmentCorrections: List<ReplenishmentCorrection>,
    private val consumptionCorrections: List<ConsumptionCorrection>,
) {
    fun currentQuantity(): Int {
        val replenished = replenishments.asList().sumOf { effective(it).value() }
        val consumed = consumptions.asList().sumOf { effective(it).value() }
        return replenished - consumed
    }

    fun needsReplenishment(): Boolean {
        val minimum = product.minimumStock?.let { it() } ?: return false
        return currentQuantity() < minimum
    }

    fun shortage(): Int {
        val minimum = product.minimumStock?.let { it() } ?: 0
        return (minimum - currentQuantity()).coerceAtLeast(0)
    }

    private fun effective(event: Replenishment): EffectiveQuantity {
        val latest =
            replenishmentCorrections
                .filter { it.target == event }
                .maxByOrNull { it.correctedAt() }
        return EffectiveQuantity(event.quantity, latest?.correctedQuantity)
    }

    private fun effective(event: Consumption): EffectiveQuantity {
        val latest =
            consumptionCorrections
                .filter { it.target == event }
                .maxByOrNull { it.correctedAt() }
        return EffectiveQuantity(event.quantity, latest?.correctedQuantity)
    }
}
