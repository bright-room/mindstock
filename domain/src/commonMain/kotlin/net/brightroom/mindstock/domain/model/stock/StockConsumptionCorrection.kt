package net.brightroom.mindstock.domain.model.stock

import kotlin.time.Instant
import net.brightroom.mindstock.domain.model.user.UserId

class StockConsumptionCorrection(
    val id: StockConsumptionCorrectionId,
    internal val stockConsumptionId: StockConsumptionId,
    internal val correctedQuantity: Quantity,
    internal val reason: Reason,
    internal val correctedBy: UserId,
    internal val createdAt: Instant,
)
