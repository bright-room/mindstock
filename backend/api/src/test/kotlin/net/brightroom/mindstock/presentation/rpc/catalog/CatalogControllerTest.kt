@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package net.brightroom.mindstock.presentation.rpc.catalog

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import net.brightroom.mindstock.application.service.catalog.CatalogService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.model.catalog.SearchLimit
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItems
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

class CatalogControllerTest :
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
