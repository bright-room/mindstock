package net.brightroom.mindstock.application.usecase.catalog

import net.brightroom.mindstock.application.repository.catalog.CatalogItemRepository
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId

class FindCatalogItemByIdHandler(
    private val catalogItemRepository: CatalogItemRepository,
) {
    fun handle(id: CatalogItemId): CatalogItem? = catalogItemRepository.findById(id)
}
