package net.brightroom.mindstock.application.usecase.catalog

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.repository.catalog.CatalogItemRegisterRepository

class RegisterCatalogItemHandler(
    private val catalogItemRegisterRepository: CatalogItemRegisterRepository,
) {
    fun handle(
        name: CatalogItemName,
        unit: CatalogItemUnit,
        createdBy: User,
    ): CatalogItem = catalogItemRegisterRepository.register(name, unit, createdBy)
}
