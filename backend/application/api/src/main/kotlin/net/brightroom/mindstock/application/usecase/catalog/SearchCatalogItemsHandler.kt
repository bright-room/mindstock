package net.brightroom.mindstock.application.usecase.catalog

import net.brightroom.mindstock.domain.model.catalog.CatalogItems
import net.brightroom.mindstock.domain.repository.catalog.CatalogItemRepository

class SearchCatalogItemsHandler(
    private val catalogItemRepository: CatalogItemRepository,
) {
    fun handle(
        query: String,
        limit: Int = 50,
    ): CatalogItems = catalogItemRepository.search(query, limit)
}
