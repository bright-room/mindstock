package net.brightroom.mindstock.presentation.rpc.stock

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.application.ApplicationCall
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import net.brightroom.mindstock.application.usecase.stock.ConsumeStockHandler
import net.brightroom.mindstock.application.usecase.stock.GetMovementHistoryHandler
import net.brightroom.mindstock.application.usecase.stock.GetStockHandler
import net.brightroom.mindstock.application.usecase.stock.ListStocksHandler
import net.brightroom.mindstock.application.usecase.stock.ReplenishStockHandler
import net.brightroom.mindstock.configuration.auth.actor
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.stock.Stock
import net.brightroom.mindstock.domain.model.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.domain.repository.household.HouseholdRepository
import net.brightroom.mindstock.domain.repository.product.ProductRepository
import net.brightroom.mindstock.domain.repository.user.UserRepository
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class StockRpcServiceImplTest :
    FunSpec({
        afterTest { unmockkAll() }

        test("get resolves actor, product then delegates to GetStockHandler") {
            val getStockHandler = mockk<GetStockHandler>()
            val listStocksHandler = mockk<ListStocksHandler>()
            val movementHistoryHandler = mockk<GetMovementHistoryHandler>()
            val replenishHandler = mockk<ReplenishStockHandler>()
            val consumeHandler = mockk<ConsumeStockHandler>()
            val productRepository = mockk<ProductRepository>()
            val householdRepository = mockk<HouseholdRepository>()
            val userRepository = mockk<UserRepository>()
            val call = mockk<ApplicationCall>()

            val user =
                User(
                    id = UserId(Uuid.parse("00000000-0000-0000-0000-000000000001")),
                    authIdentity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-1")),
                    displayName = DisplayName("Alice"),
                )
            val productId = ProductId(Uuid.parse("00000000-0000-0000-0000-000000000004"))
            val catalogItem =
                CatalogItem(
                    id = CatalogItemId(Uuid.parse("00000000-0000-0000-0000-000000000003")),
                    name = CatalogItemName("Milk"),
                    unit = CatalogItemUnit("L"),
                )
            val product =
                Product(
                    id = productId,
                    catalogItem = catalogItem,
                    minimumStock = null,
                    archived = false,
                )
            val stock = Stock(product = product, movements = StockMovements(emptyList()))

            mockkStatic("net.brightroom.mindstock.configuration.auth.ActorResolverKt")
            every { call.actor(userRepository) } returns user
            every { productRepository.findById(productId) } returns product
            every { getStockHandler.handle(product) } returns stock

            val impl =
                StockRpcServiceImpl(
                    getStock = getStockHandler,
                    listStocks = listStocksHandler,
                    getMovementHistory = movementHistoryHandler,
                    replenishStock = replenishHandler,
                    consumeStock = consumeHandler,
                    productRepository = productRepository,
                    householdRepository = householdRepository,
                    userRepository = userRepository,
                    call = call,
                )
            impl.get(productId) shouldBe stock
        }
    })
