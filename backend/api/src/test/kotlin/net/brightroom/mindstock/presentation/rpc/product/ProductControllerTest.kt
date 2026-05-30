package net.brightroom.mindstock.presentation.rpc.product

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import net.brightroom.mindstock.application.service.catalog.CatalogItemService
import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.product.ProductRegisterService
import net.brightroom.mindstock.application.service.product.ProductService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdMembers
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.rpc.RpcResult
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ProductControllerTest :
    FunSpec({
        afterTest { unmockkAll() }

        test("find resolves household, catalog item then delegates to ProductService") {
            val productService = mockk<ProductService>()
            val productRegisterService = mockk<ProductRegisterService>()
            val householdService = mockk<HouseholdService>()
            val catalogItemService = mockk<CatalogItemService>()

            val userId = UserId(Uuid.parse("00000000-0000-0000-0000-000000000001"))
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
                    minimumStock = MinimumStock.NotSet,
                    archived = false,
                )
            val session =
                MindstockSession(
                    identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-alice")),
                    userId = userId,
                    exp = Instant.fromEpochMilliseconds(Long.MAX_VALUE),
                    callId = Uuid.random(),
                )

            coEvery { householdService.findById(householdId) } returns household
            coEvery { catalogItemService.findById(catalogItemId) } returns catalogItem
            coEvery { productService.find(household, catalogItem) } returns product

            val impl =
                ProductController(
                    productService = productService,
                    productRegisterService = productRegisterService,
                    householdService = householdService,
                    catalogItemService = catalogItemService,
                    session = session,
                )
            runBlocking { impl.find(householdId, catalogItemId) } shouldBe RpcResult.Ok(product)
        }
    })
