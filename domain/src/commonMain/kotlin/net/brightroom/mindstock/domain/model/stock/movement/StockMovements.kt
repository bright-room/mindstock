package net.brightroom.mindstock.domain.model.stock.movement

import kotlinx.serialization.Serializable

/**
 * StockMovement のコレクション。
 *
 * netQuantity は補充を正・消費を負として全 movement を線形集計した正味数量。
 * `Stock.currentQuantity()` はこれをそのまま使う。
 */
@Serializable
data class StockMovements(
    val list: List<StockMovement>,
) {
    fun asList(): List<StockMovement> = list.toList()

    val size: Int get() = list.size

    fun netQuantity(): Int =
        list.sumOf { m ->
            when (m) {
                is Replenishment -> +m.quantity()
                is Consumption -> -m.quantity()
            }
        }
}
