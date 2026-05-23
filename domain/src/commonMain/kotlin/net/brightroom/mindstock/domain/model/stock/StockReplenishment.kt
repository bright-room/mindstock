package net.brightroom.mindstock.domain.model.stock

import kotlinx.datetime.Instant
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.user.UserId

class StockReplenishment(
    val id: StockReplenishmentId,
    internal val productId: ProductId,
    internal val quantity: Quantity,
    internal val occurredAt: OccurredAt,
    internal val actedBy: UserId,
    internal val note: Note,
    internal val createdAt: Instant,
)
