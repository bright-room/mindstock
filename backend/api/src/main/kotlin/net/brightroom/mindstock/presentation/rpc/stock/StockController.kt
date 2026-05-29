package net.brightroom.mindstock.presentation.rpc.stock

import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.service.stock.StockRegisterService
import net.brightroom.mindstock.application.service.stock.StockService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.transaction.tx
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.Stock
import net.brightroom.mindstock.domain.model.stock.Stocks
import net.brightroom.mindstock.domain.model.stock.movement.StockMovements
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import net.brightroom.mindstock.rpc.StockRpcService
import org.jetbrains.exposed.v1.jdbc.Database

class StockController(
    private val stockService: StockService,
    private val stockRegisterService: StockRegisterService,
    private val productRepository: ProductRepository,
    private val householdRepository: HouseholdRepository,
    private val session: MindstockSession,
    private val database: Database,
) : StockRpcService {
    override suspend fun get(productId: ProductId): RpcResult<Stock, RpcError> =
        tx(database, session) {
            val product =
                productRepository.findById(productId)
                    ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "product", id = "$productId"))
            RpcResult.Ok(stockService.get(product))
        }

    override suspend fun list(householdId: HouseholdId): RpcResult<Stocks, RpcError> =
        tx(database, session) {
            val household =
                householdRepository.findById(householdId)
                    ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "household", id = "$householdId"))
            RpcResult.Ok(stockService.list(household))
        }

    override suspend fun movementHistory(
        productId: ProductId,
        limit: Int,
    ): RpcResult<StockMovements, RpcError> =
        tx(database, session) {
            val product =
                productRepository.findById(productId)
                    ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "product", id = "$productId"))
            RpcResult.Ok(stockService.getMovementHistory(product, limit))
        }

    override suspend fun replenish(
        productId: ProductId,
        qty: Quantity,
        occurredAt: OccurredAt,
        note: Note,
    ): RpcResult<Unit, RpcError> =
        tx(database, session) {
            val product =
                productRepository.findById(productId)
                    ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "product", id = "$productId"))
            stockRegisterService.replenish(product, qty, occurredAt, requireNotNull(session.userId), note)
            RpcResult.Ok(Unit)
        }

    override suspend fun consume(
        productId: ProductId,
        qty: Quantity,
        occurredAt: OccurredAt,
        note: Note,
    ): RpcResult<Unit, RpcError> =
        tx(database, session) {
            val product =
                productRepository.findById(productId)
                    ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "product", id = "$productId"))
            stockRegisterService.consume(product, qty, occurredAt, requireNotNull(session.userId), note)
            RpcResult.Ok(Unit)
        }
}
