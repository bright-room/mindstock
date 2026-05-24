package net.brightroom.mindstock.application.usecase.catalog

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.repository.catalog.CatalogItemRepository

class FindCatalogItemByIdHandler(
    private val catalogItemRepository: CatalogItemRepository,
) {
    fun handle(id: CatalogItemId): CatalogItem? = catalogItemRepository.findById(id)
}
