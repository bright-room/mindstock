package net.brightroom.mindstock.domain.model.product

import kotlinx.datetime.Instant
import net.brightroom.mindstock.domain.model.user.UserId

class ProductMinimumStock(
    val id: ProductMinimumStockId,
    internal val productId: ProductId,
    internal val minimumStock: MinimumStock,
    internal val editedBy: UserId,
    internal val createdAt: Instant,
)
