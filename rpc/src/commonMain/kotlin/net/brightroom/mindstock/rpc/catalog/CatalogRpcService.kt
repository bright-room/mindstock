package net.brightroom.mindstock.rpc.catalog

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.catalog.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItems
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

@Rpc
interface CatalogRpcService {
    /** 名前等でマスタを検索する(UC11)。 */
    suspend fun search(
        query: String,
        limit: Int,
    ): RpcResult<CatalogItems, RpcError>

    /** JAN で照会(UC11,12。マスタ→外部 API。無ければ NotFound)。 */
    suspend fun lookupByJan(jan: Jan): RpcResult<CatalogItem, RpcError>
}
