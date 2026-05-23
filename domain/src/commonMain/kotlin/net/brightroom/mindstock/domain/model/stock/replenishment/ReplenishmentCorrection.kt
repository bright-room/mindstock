package net.brightroom.mindstock.domain.model.stock.replenishment

import net.brightroom.mindstock.domain.model.stock.CorrectedAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.Reason
import net.brightroom.mindstock.domain.model.user.User

/**
 * 補充イベントへの訂正。
 *
 * target に元イベントを composition で保持。
 * correctedAt は「いつ訂正されたか」(DB の created_at を読み替え)。
 */
data class ReplenishmentCorrection(
    val target: Replenishment,
    val correctedQuantity: Quantity,
    val reason: Reason,
    val corrector: User,
    val correctedAt: CorrectedAt,
)
