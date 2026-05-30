package net.brightroom.mindstock.application.service.catalog

import net.brightroom.mindstock.application.repository.catalog.CatalogItemRegisterRepository
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.user.UserId

class CatalogItemRegisterService(
    private val catalogItemRegisterRepository: CatalogItemRegisterRepository,
) {
    suspend fun register(
        name: CatalogItemName,
        unit: CatalogItemUnit,
        createdBy: UserId,
    ): CatalogItem = catalogItemRegisterRepository.register(name, unit, createdBy)

    suspend fun revise(
        catalogItem: CatalogItem,
        newName: CatalogItemName,
        newUnit: CatalogItemUnit,
        editedBy: UserId,
    ) {
        catalogItemRegisterRepository.revise(catalogItem, newName, newUnit, editedBy)
    }
}
