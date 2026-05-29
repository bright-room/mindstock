package net.brightroom.mindstock.presentation.rpc.catalog

import net.brightroom.mindstock.application.repository.catalog.CatalogItemRepository
import net.brightroom.mindstock.application.service.catalog.CatalogItemRegisterService
import net.brightroom.mindstock.application.service.catalog.CatalogItemService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.transaction.tx
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.catalog.CatalogItems
import net.brightroom.mindstock.rpc.CatalogRpcService
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import org.jetbrains.exposed.v1.jdbc.Database

class CatalogController(
    private val catalogItemService: CatalogItemService,
    private val catalogItemRegisterService: CatalogItemRegisterService,
    private val catalogItemRepository: CatalogItemRepository,
    private val session: MindstockSession,
    private val database: Database,
) : CatalogRpcService {
    override suspend fun search(
        query: String,
        limit: Int,
    ): RpcResult<CatalogItems, RpcError> = tx(database, session) { RpcResult.Ok(catalogItemService.search(query, limit)) }

    override suspend fun findById(id: CatalogItemId): RpcResult<CatalogItem?, RpcError> =
        tx(database, session) { RpcResult.Ok(catalogItemService.findById(id)) }

    override suspend fun register(
        name: CatalogItemName,
        unit: CatalogItemUnit,
    ): RpcResult<CatalogItem, RpcError> =
        tx(database, session) {
            RpcResult.Ok(catalogItemRegisterService.register(name, unit, requireNotNull(session.userId)))
        }

    override suspend fun revise(
        id: CatalogItemId,
        newName: CatalogItemName,
        newUnit: CatalogItemUnit,
    ): RpcResult<Unit, RpcError> =
        tx(database, session) {
            val catalogItem =
                catalogItemRepository.findById(id)
                    ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "catalog item", id = "$id"))
            catalogItemRegisterService.revise(catalogItem, newName, newUnit, requireNotNull(session.userId))
            RpcResult.Ok(Unit)
        }
}
