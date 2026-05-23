package net.brightroom.mindstock.domain.model.stock.consumption

import net.brightroom.mindstock.domain.model.stock.CorrectedAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.Reason
import net.brightroom.mindstock.domain.model.user.User

/**
 * 消費イベントへの訂正。
 */
data class ConsumptionCorrection(
    val target: Consumption,
    val correctedQuantity: Quantity,
    val reason: Reason,
    val corrector: User,
    val correctedAt: CorrectedAt,
)
