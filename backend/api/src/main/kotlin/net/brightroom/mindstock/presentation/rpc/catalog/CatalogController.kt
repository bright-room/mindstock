package net.brightroom.mindstock.presentation.rpc.catalog

import net.brightroom.mindstock.application.service.catalog.CatalogService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.guard.guarded
import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItems
import net.brightroom.mindstock.rpc.catalog.CatalogRpcService
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

class CatalogController(
    private val catalogService: CatalogService,
    private val session: MindstockSession,
) : CatalogRpcService {
    override suspend fun search(
        name: CatalogItemName,
        limit: Int,
    ): RpcResult<CatalogItems, RpcError> = guarded(session) { RpcResult.Ok(catalogService.search(name, limit)) }

    override suspend fun lookupByJan(jan: Jan): RpcResult<CatalogItem, RpcError> =
        guarded(session) { RpcResult.Ok(catalogService.lookupByJan(jan)) }
}
