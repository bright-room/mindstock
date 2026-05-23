package net.brightroom.mindstock.domain.model.stock.replenishment

import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.user.User

/**
 * 在庫補充イベント。
 *
 * `id` は訂正対象の照合に必要(同一内容の事実が複数あった場合の曖昧性回避)。
 * domain ロジックで id 比較は書かない慣習で運用。
 */
data class Replenishment(
    val id: ReplenishmentId,
    val product: Product,
    val quantity: Quantity,
    val occurredAt: OccurredAt,
    val actor: User,
    val note: Note,
)
