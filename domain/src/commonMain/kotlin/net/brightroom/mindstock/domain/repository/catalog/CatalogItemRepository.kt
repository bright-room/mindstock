package net.brightroom.mindstock.domain.repository.catalog

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItems

interface CatalogItemRepository {
    /** 名前部分一致検索。 */
    fun search(
        query: String,
        limit: Int = 50,
    ): CatalogItems

    /** id 引き(主に RPC 経由)。 */
    fun findById(id: CatalogItemId): CatalogItem?
}
