package net.brightroom.mindstock.presentation.rpc.stock

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.application.ApplicationCall
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.application.service.stock.StockRegisterService
import net.brightroom.mindstock.application.service.stock.StockService
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
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class StockControllerTest :
    FunSpec({
        afterTest { unmockkAll() }

        test("get resolves actor, product then delegates to StockService") {
            val stockService = mockk<StockService>()
            val stockRegisterService = mockk<StockRegisterService>()
            val productRepository = mockk<ProductRepository>()
            val householdRepository = mockk<HouseholdRepository>()
            val userRepository = mockk<UserRepository>()
            val call = mockk<ApplicationCall>()
            val database = mockk<Database>()

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

            mockkStatic(ApplicationCall::actor)
            every { call.actor(userRepository) } returns user
            every { productRepository.findById(productId) } returns product
            every { stockService.get(product) } returns stock

            mockkStatic("net.brightroom.mindstock.configuration.transaction.TransactionKt")
            coEvery {
                net.brightroom.mindstock.configuration.transaction
                    .tx<Any?>(any(), any())
            } coAnswers {
                val block = arg<suspend () -> Any?>(1)
                block()
            }

            val impl =
                StockController(
                    stockService = stockService,
                    stockRegisterService = stockRegisterService,
                    productRepository = productRepository,
                    householdRepository = householdRepository,
                    userRepository = userRepository,
                    call = call,
                    database = database,
                )
            impl.get(productId) shouldBe stock
        }
    })
