package net.brightroom.mindstock.application.repository.catalog

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItems

interface CatalogItemRepository {
    /** 名前部分一致検索。 */
    suspend fun search(
        query: String,
        limit: Int = 50,
    ): CatalogItems

    /**
     * id 引き(主に RPC 経由)。
     * 該当 catalog item が存在しなければ `ResourceNotFoundException` を throw する。
     */
    suspend fun findById(id: CatalogItemId): CatalogItem
}
