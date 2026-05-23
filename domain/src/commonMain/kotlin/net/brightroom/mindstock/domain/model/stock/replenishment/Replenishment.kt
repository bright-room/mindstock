package net.brightroom.mindstock.domain.model.stock.replenishment

import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.user.User

/**
 * 在庫補充イベント。
 *
 * id を持たない(順序は occurredAt、参照は composition で行う)。
 * Repository 実装での domain object と DB 行の対応付け方法は Plan 4-5 で設計。
 */
data class Replenishment(
    val product: Product,
    val quantity: Quantity,
    val occurredAt: OccurredAt,
    val actor: User,
    val note: Note,
)
