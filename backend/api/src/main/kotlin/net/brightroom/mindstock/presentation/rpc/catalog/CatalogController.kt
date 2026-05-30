package net.brightroom.mindstock.presentation.rpc.catalog

import net.brightroom.mindstock.application.service.catalog.CatalogItemRegisterService
import net.brightroom.mindstock.application.service.catalog.CatalogItemService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.rpc.rpcBoundary
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.catalog.CatalogItems
import net.brightroom.mindstock.rpc.CatalogRpcService
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult

class CatalogController(
    private val catalogItemService: CatalogItemService,
    private val catalogItemRegisterService: CatalogItemRegisterService,
    private val session: MindstockSession,
) : CatalogRpcService {
    override suspend fun search(
        query: String,
        limit: Int,
    ): RpcResult<CatalogItems, RpcError> = rpcBoundary(session) { catalogItemService.search(query, limit) }

    override suspend fun findById(id: CatalogItemId): RpcResult<CatalogItem, RpcError> =
        rpcBoundary(session) { catalogItemService.findById(id) }

    override suspend fun register(
        name: CatalogItemName,
        unit: CatalogItemUnit,
    ): RpcResult<CatalogItem, RpcError> =
        rpcBoundary(session) {
            catalogItemRegisterService.register(name, unit, requireNotNull(session.userId))
        }

    override suspend fun revise(
        id: CatalogItemId,
        newName: CatalogItemName,
        newUnit: CatalogItemUnit,
    ): RpcResult<Unit, RpcError> =
        rpcBoundary(session) {
            val catalogItem = catalogItemService.findById(id)
            catalogItemRegisterService.revise(catalogItem, newName, newUnit, requireNotNull(session.userId))
        }
}
