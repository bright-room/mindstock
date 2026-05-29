package net.brightroom.mindstock.presentation.rpc.catalog

import io.ktor.server.application.ApplicationCall
import net.brightroom.mindstock.application.repository.catalog.CatalogItemRepository
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.application.service.catalog.CatalogItemRegisterService
import net.brightroom.mindstock.application.service.catalog.CatalogItemService
import net.brightroom.mindstock.configuration.auth.actor
import net.brightroom.mindstock.configuration.error.NotFoundException
import net.brightroom.mindstock.configuration.transaction.tx
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.catalog.CatalogItems
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.rpc.CatalogRpcService
import org.jetbrains.exposed.v1.jdbc.Database

class CatalogController(
    private val catalogItemService: CatalogItemService,
    private val catalogItemRegisterService: CatalogItemRegisterService,
    private val catalogItemRepository: CatalogItemRepository,
    private val userRepository: UserRepository,
    private val call: ApplicationCall,
    private val database: Database,
) : CatalogRpcService {
    // Memoized for the lifetime of this per-connection Service Impl.
    // NOTE: a rename within the same connection won't refresh this cache until reconnect.
    private val actor: User by lazy { call.actor(userRepository) }

    override suspend fun search(
        query: String,
        limit: Int,
    ): CatalogItems =
        tx(database) {
            actor
            catalogItemService.search(query, limit)
        }

    override suspend fun findById(id: CatalogItemId): CatalogItem? =
        tx(database) {
            actor
            catalogItemService.findById(id)
        }

    override suspend fun register(
        name: CatalogItemName,
        unit: CatalogItemUnit,
    ): CatalogItem = tx(database) { catalogItemRegisterService.register(name, unit, actor) }

    override suspend fun revise(
        id: CatalogItemId,
        newName: CatalogItemName,
        newUnit: CatalogItemUnit,
    ) = tx(database) {
        val catalogItem =
            catalogItemRepository.findById(id)
                ?: throw NotFoundException("catalog item not found: $id")
        catalogItemRegisterService.revise(catalogItem, newName, newUnit, actor)
    }
}
