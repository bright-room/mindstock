package net.brightroom.mindstock.domain.model.inventory.stock.movement

import kotlinx.serialization.Serializable

@Serializable
data class StockMovements(
    val list: List<StockMovement>,
) {
    fun size(): Int = list.size

    fun add(movement: StockMovement): StockMovements = StockMovements(list + movement)

    fun netQuantity(): Int {
        val latestCorrection: Map<MovementId, Correction> =
            list
                .filterIsInstance<Correction>()
                .groupBy { it.target }
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
