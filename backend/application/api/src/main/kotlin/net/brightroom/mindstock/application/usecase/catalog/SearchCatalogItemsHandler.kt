package net.brightroom.mindstock.application.usecase.catalog

import net.brightroom.mindstock.application.repository.catalog.CatalogItemRepository
import net.brightroom.mindstock.domain.model.catalog.CatalogItems

class SearchCatalogItemsHandler(
    private val catalogItemRepository: CatalogItemRepository,
) {
    fun handle(
        query: String,
        limit: Int = 50,
    ): CatalogItems = catalogItemRepository.search(query, limit)
}
