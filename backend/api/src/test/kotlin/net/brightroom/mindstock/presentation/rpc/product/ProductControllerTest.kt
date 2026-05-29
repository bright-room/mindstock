package net.brightroom.mindstock.presentation.rpc.product

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.application.ApplicationCall
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import net.brightroom.mindstock.application.repository.catalog.CatalogItemRepository
import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.application.service.product.ProductRegisterService
import net.brightroom.mindstock.application.service.product.ProductService
import net.brightroom.mindstock.configuration.auth.actor
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdMembers
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ProductControllerTest :
    FunSpec({
        afterTest { unmockkAll() }

        test("find resolves actor, household, catalog item then delegates to ProductService") {
            val productService = mockk<ProductService>()
            val productRegisterService = mockk<ProductRegisterService>()
            val householdRepository = mockk<HouseholdRepository>()
            val catalogItemRepository = mockk<CatalogItemRepository>()
            val productRepository = mockk<ProductRepository>()
            val userRepository = mockk<UserRepository>()
            val call = mockk<ApplicationCall>()
            val database = mockk<Database>()

            val user =
                User(
                    id = UserId(Uuid.parse("00000000-0000-0000-0000-000000000001")),
                    authIdentity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-1")),
                    displayName = DisplayName("Alice"),
                )
            val householdId = HouseholdId(Uuid.parse("00000000-0000-0000-0000-000000000002"))
            val household = Household(id = householdId, members = HouseholdMembers(emptyList()))
            val catalogItemId = CatalogItemId(Uuid.parse("00000000-0000-0000-0000-000000000003"))
            val catalogItem =
                CatalogItem(
                    id = catalogItemId,
                    name = CatalogItemName("Milk"),
                    unit = CatalogItemUnit("L"),
                )
            val product =
                Product(
                    id = ProductId(Uuid.parse("00000000-0000-0000-0000-000000000004")),
                    catalogItem = catalogItem,
                    minimumStock = null,
                    archived = false,
                )

            mockkStatic(ApplicationCall::actor)
            every { call.actor(userRepository) } returns user
            every { householdRepository.findById(householdId) } returns household
            every { catalogItemRepository.findById(catalogItemId) } returns catalogItem
            every { productService.find(household, catalogItem) } returns product

            mockkStatic("net.brightroom.mindstock.configuration.transaction.TransactionKt")
            coEvery {
                net.brightroom.mindstock.configuration.transaction
                    .tx<Any?>(any(), any())
            } coAnswers {
                val block = arg<suspend () -> Any?>(1)
                block()
            }

            val impl =
                ProductController(
                    productService = productService,
                    productRegisterService = productRegisterService,
                    householdRepository = householdRepository,
                    catalogItemRepository = catalogItemRepository,
                    productRepository = productRepository,
                    userRepository = userRepository,
                    call = call,
                    database = database,
                )
            impl.find(householdId, catalogItemId) shouldBe product
        }
    })
