package net.brightroom.mindstock.domain.model.stock

import net.brightroom.mindstock.domain.model.user.UserId
import kotlin.time.Instant

class StockReplenishmentCorrection(
    val id: StockReplenishmentCorrectionId,
    internal val stockReplenishmentId: StockReplenishmentId,
    internal val correctedQuantity: Quantity,
    internal val reason: Reason,
    internal val correctedBy: UserId,
    internal val createdAt: Instant,
)
