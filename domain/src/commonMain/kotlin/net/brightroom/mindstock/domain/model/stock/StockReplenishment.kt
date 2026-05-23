package net.brightroom.mindstock.domain.model.stock

import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.user.UserId
import kotlin.time.Instant

class StockReplenishment(
    val id: StockReplenishmentId,
    internal val productId: ProductId,
    internal val quantity: Quantity,
    internal val occurredAt: OccurredAt,
    internal val actedBy: UserId,
    internal val note: Note,
    internal val createdAt: Instant,
)
