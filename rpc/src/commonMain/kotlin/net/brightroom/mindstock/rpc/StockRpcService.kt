package net.brightroom.mindstock.rpc

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.Stock
import net.brightroom.mindstock.domain.model.stock.movement.StockMovements

@Rpc
interface StockRpcService {
    suspend fun get(productId: ProductId): RpcResult<Stock, RpcError>

    suspend fun list(householdId: HouseholdId): RpcResult<List<Stock>, RpcError>

    suspend fun movementHistory(
        productId: ProductId,
        limit: Int,
    ): RpcResult<StockMovements, RpcError>

    suspend fun replenish(
        productId: ProductId,
        qty: Quantity,
        occurredAt: OccurredAt,
        note: Note,
    ): RpcResult<Unit, RpcError>

    suspend fun consume(
        productId: ProductId,
        qty: Quantity,
        occurredAt: OccurredAt,
        note: Note,
    ): RpcResult<Unit, RpcError>
}
