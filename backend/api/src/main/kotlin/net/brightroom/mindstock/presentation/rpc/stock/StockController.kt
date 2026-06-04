package net.brightroom.mindstock.presentation.rpc.stock

import net.brightroom.mindstock.application.service.stock.StockService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.auth.requireResidentId
import net.brightroom.mindstock.configuration.guard.guarded
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
        guarded(session) { RpcResult.Ok(stockService.history(productId, session.requireResidentId())) }

    override suspend fun activity(householdId: HouseholdId): RpcResult<ActivityFeed, RpcError> =
        guarded(session) {
            val stocks = stockService.activity(householdId, session.requireResidentId())
            val entries =
                stocks.list
                    .flatMap { stock -> stock.movements.list.map { ActivityEntry(stock.product, it) } }
                    .sortedByDescending { it.movement.occurredAt() }
            RpcResult.Ok(ActivityFeed(entries))
        }
}
