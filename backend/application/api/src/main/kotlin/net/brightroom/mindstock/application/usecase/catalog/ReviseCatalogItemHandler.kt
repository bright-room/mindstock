package net.brightroom.mindstock.application.usecase.catalog

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.repository.catalog.CatalogItemRegisterRepository

class ReviseCatalogItemHandler(
    private val catalogItemRegisterRepository: CatalogItemRegisterRepository,
) {
    fun handle(
        catalogItem: CatalogItem,
        newName: CatalogItemName,
        newUnit: CatalogItemUnit,
        editedBy: User,
    ) {
        catalogItemRegisterRepository.revise(catalogItem, newName, newUnit, editedBy)
    }
}
