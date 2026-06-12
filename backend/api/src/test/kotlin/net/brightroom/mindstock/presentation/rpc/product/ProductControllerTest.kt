@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.presentation.rpc.product

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import net.brightroom.mindstock.application.service.product.ProductService
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.rpc.result.RpcResult
import net.brightroom.mindstock.testfixtures.buildRegisteredSession

class ProductControllerTest :
    FunSpec({
        val residentId = ResidentId.create()
        val session = buildRegisteredSession(residentId)

        val productService = mockk<ProductService>()
        val controller = ProductController(productService, session)

        test("list は ProductService.list(householdId, residentId) の結果を Ok で包んで返す") {
            val householdId = HouseholdId.create()
            val stocks = Stocks(emptyList())
            every { productService.list(householdId, residentId) } returns stocks

            controller.list(householdId) shouldBe RpcResult.Ok(stocks)
        }
    })
