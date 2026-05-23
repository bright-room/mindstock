package net.brightroom.mindstock.domain.model.catalog

import kotlinx.datetime.Instant
import net.brightroom.mindstock.domain.model.user.UserId

class CatalogItem(
    val id: CatalogItemId,
    internal val createdBy: UserId,
    internal val createdAt: Instant,
    /** 最新リビジョンの名前と単位。新規登録直後は最初のリビジョンの値が入る。 */
    internal val latestName: CatalogItemName,
    internal val latestUnit: CatalogItemUnit,
)
