package net.brightroom.mindstock.domain.model.stock.movement

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.user.profile.Profile

/**
 * 在庫変動の事実(append-only)。
 *
 * id は持たない(domain 上で参照する操作がない。BIGSERIAL は DB の関心事)。
 * product は集約ルート Stock が保持するため、movement には含めない。
 */
@Serializable
sealed interface StockMovement {
    val quantity: Quantity
    val occurredAt: OccurredAt
    val actor: Profile
    val note: Note
}
