package net.brightroom.mindstock.presentation.rpc.stock

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.product.ProductService
import net.brightroom.mindstock.application.service.stock.StockRegisterService
import net.brightroom.mindstock.application.service.stock.StockService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.stock.Stock
import net.brightroom.mindstock.domain.model.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class StockControllerTest :
    FunSpec({
        afterTest { unmockkAll() }

        test("get resolves product then delegates to StockService") {
            val stockService = mockk<StockService>()
            val stockRegisterService = mockk<StockRegisterService>()
            val productService = mockk<ProductService>()
            val householdService = mockk<HouseholdService>()
            val database = mockk<Database>()

            val userId = UserId(Uuid.parse("00000000-0000-0000-0000-000000000001"))
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
                    minimumStock = MinimumStock.NotSet,
                    archived = false,
                )
            val stock = Stock(product = product, movements = StockMovements(emptyList()))
            val session =
                MindstockSession(
                    identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-alice")),
                    userId = userId,
                    exp = Instant.fromEpochMilliseconds(Long.MAX_VALUE),
                    callId = Uuid.random(),
                )

            every { productService.findById(productId) } returns product
            every { stockService.get(product) } returns stock

            mockkStatic("net.brightroom.mindstock.configuration.transaction.TransactionKt")
            coEvery {
                net.brightroom.mindstock.configuration.transaction
                    .tx<Stock>(any(), any(), any())
            } coAnswers {
                val block = arg<suspend () -> RpcResult<Stock, RpcError>>(2)
                block()
            }

            val impl =
                StockController(
                    stockService = stockService,
                    stockRegisterService = stockRegisterService,
                    productService = productService,
                    householdService = householdService,
                    session = session,
                    database = database,
                )
            runBlocking { impl.get(productId) } shouldBe RpcResult.Ok(stock)
        }
    })
