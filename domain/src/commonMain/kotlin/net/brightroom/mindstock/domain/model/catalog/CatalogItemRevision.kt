package net.brightroom.mindstock.domain.model.catalog

import kotlinx.datetime.Instant
import net.brightroom.mindstock.domain.model.user.UserId

public class CatalogItemRevision(
    public val id: CatalogItemRevisionId,
    internal val catalogItemId: CatalogItemId,
    internal val name: CatalogItemName,
    internal val unit: CatalogItemUnit,
    internal val editedBy: UserId,
    internal val createdAt: Instant,
)
