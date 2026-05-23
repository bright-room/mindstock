package net.brightroom.mindstock.domain.model.product

import kotlinx.datetime.Instant
import net.brightroom.mindstock.domain.model.user.UserId

class ProductArchive(
    val id: ProductArchiveId,
    internal val productId: ProductId,
    internal val archivedBy: UserId,
    internal val createdAt: Instant,
)
