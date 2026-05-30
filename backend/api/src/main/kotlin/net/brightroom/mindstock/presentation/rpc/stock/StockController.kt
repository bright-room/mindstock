package net.brightroom.mindstock.presentation.rpc.stock

import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.product.ProductService
import net.brightroom.mindstock.application.service.stock.StockRegisterService
import net.brightroom.mindstock.application.service.stock.StockService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.rpc.rpcBoundary
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.stock.Stock
import net.brightroom.mindstock.domain.model.stock.Stocks
import net.brightroom.mindstock.domain.model.stock.movement.Note
import net.brightroom.mindstock.domain.model.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.stock.movement.Quantity
import net.brightroom.mindstock.domain.model.stock.movement.StockMovements
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import net.brightroom.mindstock.rpc.StockRpcService

class StockController(
    private val stockService: StockService,
    private val stockRegisterService: StockRegisterService,
    private val productService: ProductService,
    private val householdService: HouseholdService,
    private val session: MindstockSession,
) : StockRpcService {
    override suspend fun get(productId: ProductId): RpcResult<Stock, RpcError> =
        rpcBoundary(session) {
            val product = productService.findById(productId)
            stockService.get(product)
        }

    override suspend fun list(householdId: HouseholdId): RpcResult<Stocks, RpcError> =
        rpcBoundary(session) {
            val household = householdService.findById(householdId)
            stockService.list(household)
        }

    override suspend fun movementHistory(
        productId: ProductId,
        limit: Int,
    ): RpcResult<StockMovements, RpcError> =
        rpcBoundary(session) {
            val product = productService.findById(productId)
            stockService.getMovementHistory(product, limit)
        }

    override suspend fun replenish(
        productId: ProductId,
        qty: Quantity,
        occurredAt: OccurredAt,
        note: Note,
    ): RpcResult<Unit, RpcError> =
        rpcBoundary(session) {
            val product = productService.findById(productId)
            stockRegisterService.replenish(product, qty, occurredAt, requireNotNull(session.userId), note)
        }

    override suspend fun consume(
        productId: ProductId,
        qty: Quantity,
        occurredAt: OccurredAt,
        note: Note,
    ): RpcResult<Unit, RpcError> =
        rpcBoundary(session) {
            val product = productService.findById(productId)
            stockRegisterService.consume(product, qty, occurredAt, requireNotNull(session.userId), note)
        }
}
