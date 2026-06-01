package net.brightroom.mindstock.domain.model.inventory.stock.movement

import kotlinx.serialization.Serializable

@Serializable
data class StockMovements(
    val list: List<StockMovement>,
) {
    fun size(): Int = list.size

    fun add(movement: StockMovement): StockMovements = StockMovements(list + movement)

    fun hasBaseMovement(id: MovementId): Boolean =
        list.any { (it is Replenishment || it is Consumption) && it.identity == MovementIdentity.Persisted(id) }

    fun netQuantity(): Int {
        val latestCorrection: Map<MovementId, Correction> =
            list
                .filterIsInstance<Correction>()
                .groupBy { it.target }
                // 同一 target に同 occurredAt の訂正が複数ある場合は list 出現順で最初の最大値を採用(実運用では Instant 衝突は起きない前提)
                .mapValues { (_, corrections) -> corrections.maxBy { it.occurredAt() } }
        return list.sumOf { movement ->
            when (movement) {
                is Replenishment -> effectiveQuantity(movement, latestCorrection)
                is Consumption -> -effectiveQuantity(movement, latestCorrection)
                is Correction -> 0
            }
        }
    }

    private fun effectiveQuantity(
        base: StockMovement,
        latestCorrection: Map<MovementId, Correction>,
    ): Int {
        val id = (base.identity as? MovementIdentity.Persisted)?.id
        val correction = id?.let { latestCorrection[it] }
        return correction?.quantity?.invoke() ?: base.quantity()
    }
}
