package net.brightroom.mindstock.rpc

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.catalog.CatalogItems

@Rpc
interface CatalogRpcService {
    suspend fun search(
        query: String,
        limit: Int,
    ): RpcResult<CatalogItems, RpcError>

    suspend fun findById(id: CatalogItemId): RpcResult<CatalogItem?, RpcError>

    suspend fun register(
        name: CatalogItemName,
        unit: CatalogItemUnit,
    ): RpcResult<CatalogItem, RpcError>

    suspend fun revise(
        id: CatalogItemId,
        newName: CatalogItemName,
        newUnit: CatalogItemUnit,
    ): RpcResult<Unit, RpcError>
}
