package net.brightroom.mindstock.domain.model.stock.movement

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.stock.movement.Note
import net.brightroom.mindstock.domain.model.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.stock.movement.Quantity
import net.brightroom.mindstock.domain.model.user.profile.Profile

@Serializable
data class Consumption(
    override val quantity: Quantity,
    override val occurredAt: OccurredAt,
    override val actor: Profile,
    override val note: Note,
) : StockMovement
