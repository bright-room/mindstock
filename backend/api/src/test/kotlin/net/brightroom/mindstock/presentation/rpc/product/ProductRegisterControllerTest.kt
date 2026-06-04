@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package net.brightroom.mindstock.presentation.rpc.product

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.brightroom.mindstock.application.scenario.product.AdoptProductScenario
import net.brightroom.mindstock.application.service.product.ProductRegisterService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

class ProductRegisterControllerTest :
    FunSpec({
        val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub"))
        val residentId = ResidentId.create()
        val session =
            MindstockSession.Registered(
                identity,
                residentId,
                Clock.System.now().plus(1.hours),
                Uuid.random(),
            )

        val productRegisterService = mockk<ProductRegisterService>(relaxed = true)
        val adoptProductScenario = mockk<AdoptProductScenario>(relaxed = true)
        val controller = ProductRegisterController(productRegisterService, adoptProductScenario, session)

        test("adopt は AdoptProductScenario.run の結果を Ok で包んで返す") {
            val householdId = HouseholdId.create()
            val catalogItemId = CatalogItemId.create()
            val unit = ProductUnit("本")
            val minimumStock = MinimumStock(1)
            val product = Product.custom(ProductName("水"), Barcode.Unlinked, unit, minimumStock)
            every { adoptProductScenario.run(householdId, catalogItemId, unit, minimumStock, residentId) } returns product

            controller.adopt(householdId, catalogItemId, unit, minimumStock) shouldBe RpcResult.Ok(product)
        }

        test("changeUnit は ProductRegisterService.changeUnit を呼び Ok(Unit) を返す") {
            val productId = ProductId.create()
            val unit = ProductUnit("缶")

            controller.changeUnit(productId, unit) shouldBe RpcResult.Ok(Unit)
            verify { productRegisterService.changeUnit(productId, unit, residentId) }
        }
    })
