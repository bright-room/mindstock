package net.brightroom.mindstock.domain.model.stock

import kotlinx.datetime.Instant
import net.brightroom.mindstock.domain.model.user.UserId

public class StockReplenishmentCorrection(
    public val id: StockReplenishmentCorrectionId,
    internal val stockReplenishmentId: StockReplenishmentId,
    internal val correctedQuantity: Quantity,
    internal val reason: Reason,
    internal val correctedBy: UserId,
    internal val createdAt: Instant,
)
