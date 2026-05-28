package net.brightroom.mindstock.application.usecase.catalog

import net.brightroom.mindstock.application.repository.catalog.CatalogItemRegisterRepository
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.user.User

class RegisterCatalogItemHandler(
    private val catalogItemRegisterRepository: CatalogItemRegisterRepository,
) {
    fun handle(
        name: CatalogItemName,
        unit: CatalogItemUnit,
        createdBy: User,
    ): CatalogItem = catalogItemRegisterRepository.register(name, unit, createdBy)
}
