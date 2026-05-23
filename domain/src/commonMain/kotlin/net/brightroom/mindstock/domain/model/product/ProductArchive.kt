package net.brightroom.mindstock.domain.model.product

import net.brightroom.mindstock.domain.model.user.UserId
import kotlin.time.Instant

class ProductArchive(
    val id: ProductArchiveId,
    internal val productId: ProductId,
    internal val archivedBy: UserId,
    internal val createdAt: Instant,
)
