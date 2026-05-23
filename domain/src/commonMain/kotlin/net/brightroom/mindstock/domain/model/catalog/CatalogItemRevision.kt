package net.brightroom.mindstock.domain.model.catalog

import net.brightroom.mindstock.domain.model.user.UserId
import kotlin.time.Instant

class CatalogItemRevision(
    val id: CatalogItemRevisionId,
    internal val catalogItemId: CatalogItemId,
    internal val name: CatalogItemName,
    internal val unit: CatalogItemUnit,
    internal val editedBy: UserId,
    internal val createdAt: Instant,
)
