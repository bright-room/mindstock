package net.brightroom.mindstock.domain.model.inventory.stock.movement

import kotlinx.serialization.Serializable

@Serializable
sealed interface MovementIdentity {
    @Serializable
    data object Pending : MovementIdentity

    @Serializable
    data class Persisted(
        val id: MovementId,
    ) : MovementIdentity
}
