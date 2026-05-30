package net.brightroom.mindstock.presentation.rpc.catalog

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import net.brightroom.mindstock.application.service.catalog.CatalogItemRegisterService
import net.brightroom.mindstock.application.service.catalog.CatalogItemService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.model.catalog.CatalogItems
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.rpc.RpcResult
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class CatalogControllerTest :
    FunSpec({
        afterTest { unmockkAll() }

        test("search delegates to CatalogItemService (no actor resolution required for read)") {
            val catalogItemService = mockk<CatalogItemService>()
            val catalogItemRegisterService = mockk<CatalogItemRegisterService>()
            val userId = UserId(Uuid.parse("00000000-0000-0000-0000-000000000001"))
            val expected = CatalogItems(emptyList())
            val query = "milk"
            val limit = 20
            val session =
                MindstockSession(
                    identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-alice")),
                    userId = userId,
                    exp = Instant.fromEpochMilliseconds(Long.MAX_VALUE),
                    callId = Uuid.random(),
                )

            coEvery { catalogItemService.search(query, limit) } returns expected

            val impl =
                CatalogController(
                    catalogItemService = catalogItemService,
                    catalogItemRegisterService = catalogItemRegisterService,
                    session = session,
                )
            runBlocking { impl.search(query, limit) } shouldBe RpcResult.Ok(expected)
        }
    })
