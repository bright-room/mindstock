package net.brightroom.mindstock.application.service.catalog

import net.brightroom.mindstock.application.repository.catalog.CatalogItemRegisterRepository
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.user.User

class CatalogItemRegisterService(
    private val catalogItemRegisterRepository: CatalogItemRegisterRepository,
) {
    fun register(
        name: CatalogItemName,
        unit: CatalogItemUnit,
        createdBy: User,
    ): CatalogItem = catalogItemRegisterRepository.register(name, unit, createdBy)

    fun revise(
        catalogItem: CatalogItem,
        newName: CatalogItemName,
        newUnit: CatalogItemUnit,
        editedBy: User,
    ) {
        catalogItemRegisterRepository.revise(catalogItem, newName, newUnit, editedBy)
    }
}
