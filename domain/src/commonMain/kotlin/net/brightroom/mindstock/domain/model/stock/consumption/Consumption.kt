package net.brightroom.mindstock.domain.model.stock.consumption

import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.user.User

/**
 * 在庫消費イベント。
 *
 * `id` の扱いは [Replenishment] と同様。
 */
data class Consumption(
    val id: ConsumptionId,
    val product: Product,
    val quantity: Quantity,
    val occurredAt: OccurredAt,
    val actor: User,
    val note: Note,
)
