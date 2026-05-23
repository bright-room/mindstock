package net.brightroom.mindstock.domain.model.catalog

import kotlin.time.Instant
import net.brightroom.mindstock.domain.model.user.UserId

class CatalogItemRevision(
    val id: CatalogItemRevisionId,
    internal val catalogItemId: CatalogItemId,
    internal val name: CatalogItemName,
    internal val unit: CatalogItemUnit,
    internal val editedBy: UserId,
    internal val createdAt: Instant,
)
