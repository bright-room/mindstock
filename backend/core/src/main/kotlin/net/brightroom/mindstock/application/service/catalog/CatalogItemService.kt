package net.brightroom.mindstock.application.service.catalog

import net.brightroom.mindstock.application.repository.catalog.CatalogItemRepository
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItems

class CatalogItemService(
    private val catalogItemRepository: CatalogItemRepository,
) {
    suspend fun findById(id: CatalogItemId): CatalogItem = catalogItemRepository.findById(id)

    suspend fun search(
        query: String,
        limit: Int = 50,
    ): CatalogItems = catalogItemRepository.search(query, limit)
}
