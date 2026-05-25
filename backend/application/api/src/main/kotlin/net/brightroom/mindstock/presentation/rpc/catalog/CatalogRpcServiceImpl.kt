package net.brightroom.mindstock.presentation.rpc.catalog

import io.ktor.server.application.ApplicationCall
import net.brightroom.mindstock.application.usecase.catalog.FindCatalogItemByIdHandler
import net.brightroom.mindstock.application.usecase.catalog.RegisterCatalogItemHandler
import net.brightroom.mindstock.application.usecase.catalog.ReviseCatalogItemHandler
import net.brightroom.mindstock.application.usecase.catalog.SearchCatalogItemsHandler
import net.brightroom.mindstock.configuration.auth.actor
import net.brightroom.mindstock.configuration.error.NotFoundException
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.catalog.CatalogItems
import net.brightroom.mindstock.domain.repository.catalog.CatalogItemRepository
import net.brightroom.mindstock.domain.repository.user.UserRepository
import net.brightroom.mindstock.presentation.rpc.CatalogRpcService

class CatalogRpcServiceImpl(
    private val searchHandler: SearchCatalogItemsHandler,
    private val findByIdHandler: FindCatalogItemByIdHandler,
    private val registerHandler: RegisterCatalogItemHandler,
    private val reviseHandler: ReviseCatalogItemHandler,
    private val catalogItemRepository: CatalogItemRepository,
    private val userRepository: UserRepository,
    private val call: ApplicationCall,
) : CatalogRpcService {
    override suspend fun search(
        query: String,
        limit: Int,
    ): CatalogItems {
        call.actor(userRepository)
        return searchHandler.handle(query, limit)
    }

    override suspend fun findById(id: CatalogItemId): CatalogItem? {
        call.actor(userRepository)
        return findByIdHandler.handle(id)
    }

    override suspend fun register(
        name: CatalogItemName,
        unit: CatalogItemUnit,
    ): CatalogItem {
        val actor = call.actor(userRepository)
        return registerHandler.handle(name, unit, actor)
    }

    override suspend fun revise(
        id: CatalogItemId,
        newName: CatalogItemName,
        newUnit: CatalogItemUnit,
    ) {
        val actor = call.actor(userRepository)
        val catalogItem =
            catalogItemRepository.findById(id)
                ?: throw NotFoundException("catalog item not found: $id")
        reviseHandler.handle(catalogItem, newName, newUnit, actor)
    }
}
