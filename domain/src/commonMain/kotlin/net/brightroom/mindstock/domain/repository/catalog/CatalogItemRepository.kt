package net.brightroom.mindstock.domain.repository.catalog

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId

interface CatalogItemRepository {
    /** catalog_items + 最新 catalog_item_revisions を joins した CatalogItem。 */
    fun findById(id: CatalogItemId): CatalogItem?

    /** 名前部分一致検索(MVP は単純な LIKE で OK)。 */
    fun search(
        query: String,
        limit: Int = 50,
    ): List<CatalogItem>
}
