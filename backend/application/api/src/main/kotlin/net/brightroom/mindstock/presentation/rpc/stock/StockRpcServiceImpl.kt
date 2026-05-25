package net.brightroom.mindstock.presentation.rpc.stock

import io.ktor.server.application.ApplicationCall
import net.brightroom.mindstock.application.usecase.stock.ConsumeStockHandler
import net.brightroom.mindstock.application.usecase.stock.GetMovementHistoryHandler
import net.brightroom.mindstock.application.usecase.stock.GetStockHandler
import net.brightroom.mindstock.application.usecase.stock.ListStocksHandler
import net.brightroom.mindstock.application.usecase.stock.ReplenishStockHandler
import net.brightroom.mindstock.configuration.auth.actor
import net.brightroom.mindstock.configuration.error.NotFoundException
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.Stock
import net.brightroom.mindstock.domain.model.stock.movement.Consumption
import net.brightroom.mindstock.domain.model.stock.movement.Replenishment
import net.brightroom.mindstock.domain.model.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.repository.household.HouseholdRepository
import net.brightroom.mindstock.domain.repository.product.ProductRepository
import net.brightroom.mindstock.domain.repository.user.UserRepository
import net.brightroom.mindstock.presentation.rpc.StockRpcService

class StockRpcServiceImpl(
    private val getStock: GetStockHandler,
    private val listStocks: ListStocksHandler,
    private val getMovementHistory: GetMovementHistoryHandler,
    private val replenishStock: ReplenishStockHandler,
    private val consumeStock: ConsumeStockHandler,
    private val productRepository: ProductRepository,
    private val householdRepository: HouseholdRepository,
    private val userRepository: UserRepository,
    private val call: ApplicationCall,
) : StockRpcService {
    // Memoized for the lifetime of this per-connection Service Impl.
    // NOTE: a rename within the same connection won't refresh this cache until reconnect.
    private val actor: User by lazy { call.actor(userRepository) }

    override suspend fun get(productId: ProductId): Stock {
        // TODO(authz): verify actor can access product $productId (member of its household)
        actor
        val product =
            productRepository.findById(productId)
                ?: throw NotFoundException("product not found: $productId")
        return getStock.handle(product)
    }

    override suspend fun list(householdId: HouseholdId): List<Stock> {
        // TODO(authz): verify actor is a member of household $householdId
        actor
        val household =
            householdRepository.findById(householdId)
                ?: throw NotFoundException("household not found: $householdId")
        return listStocks.handle(household)
    }

    override suspend fun movementHistory(
        productId: ProductId,
        limit: Int,
    ): StockMovements {
        // TODO(authz): verify actor can access product $productId (member of its household)
        actor
        val product =
            productRepository.findById(productId)
                ?: throw NotFoundException("product not found: $productId")
        return getMovementHistory.handle(product, limit)
    }

    override suspend fun replenish(
        productId: ProductId,
        qty: Quantity,
        occurredAt: OccurredAt,
        note: Note,
    ): Replenishment {
        // TODO(authz): verify actor can modify product $productId (member of its household)
        val product =
            productRepository.findById(productId)
                ?: throw NotFoundException("product not found: $productId")
        return replenishStock.handle(product, qty, occurredAt, actor, note)
    }

    override suspend fun consume(
        productId: ProductId,
        qty: Quantity,
        occurredAt: OccurredAt,
        note: Note,
    ): Consumption {
        // TODO(authz): verify actor can modify product $productId (member of its household)
        val product =
            productRepository.findById(productId)
                ?: throw NotFoundException("product not found: $productId")
        return consumeStock.handle(product, qty, occurredAt, actor, note)
    }
}
