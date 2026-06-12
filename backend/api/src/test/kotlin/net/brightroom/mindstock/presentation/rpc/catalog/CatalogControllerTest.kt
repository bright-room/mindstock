@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.presentation.rpc.catalog

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import net.brightroom.mindstock.application.service.catalog.CatalogService
import net.brightroom.mindstock.domain.model.catalog.SearchLimit
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItems
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.rpc.result.RpcResult
import net.brightroom.mindstock.testfixtures.buildRegisteredSession

class CatalogControllerTest :
    FunSpec({
        val residentId = ResidentId.create()
        val session = buildRegisteredSession(residentId)

        val catalogService = mockk<CatalogService>()
        val controller = CatalogController(catalogService, session)

        test("search は CatalogService.search の結果を Ok で包んで返す") {
            val name = CatalogItemName("米")
            val limit = SearchLimit(10)
            val expected = CatalogItems(emptyList())
            every { catalogService.search(name, limit) } returns expected

            controller.search(name, limit) shouldBe RpcResult.Ok(expected)
        }
    })
