package net.brightroom.mindstock.presentation.rpc.stock

import net.brightroom.mindstock.application.service.stock.StockService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.guard.requireRegistered
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult
import net.brightroom.mindstock.rpc.stock.ActivityEntry
import net.brightroom.mindstock.rpc.stock.ActivityFeed
import net.brightroom.mindstock.rpc.stock.StockRpcService

class StockController(
    private val stockService: StockService,
    private val session: MindstockSession,
) : StockRpcService {
    override suspend fun history(productId: ProductId): RpcResult<StockMovements, RpcError> =
        requireRegistered(session) { residentId -> RpcResult.Ok(stockService.history(productId, residentId)) }

    override suspend fun activity(householdId: HouseholdId): RpcResult<ActivityFeed, RpcError> =
        requireRegistered(session) { residentId ->
            val stocks = stockService.activity(householdId, residentId)
            val entries =
                stocks.list
                    .flatMap { stock -> stock.movements.list.map { ActivityEntry(stock.product, it) } }
                    .sortedByDescending { it.movement.occurredAt() }
            RpcResult.Ok(ActivityFeed(entries))
        }
}
