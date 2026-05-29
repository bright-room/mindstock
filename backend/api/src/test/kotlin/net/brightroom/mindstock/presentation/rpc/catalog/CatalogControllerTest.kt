package net.brightroom.mindstock.presentation.rpc.catalog

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.datetime.Instant
import net.brightroom.mindstock.application.repository.catalog.CatalogItemRepository
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.application.service.catalog.CatalogItemRegisterService
import net.brightroom.mindstock.application.service.catalog.CatalogItemService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.model.catalog.CatalogItems
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
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
class CatalogControllerTest :
    FunSpec({
        afterTest { unmockkAll() }

        test("search delegates to CatalogItemService (no actor resolution required for read)") {
            val catalogItemService = mockk<CatalogItemService>()
            val catalogItemRegisterService = mockk<CatalogItemRegisterService>()
            val catalogItemRepository = mockk<CatalogItemRepository>()
            val userRepository = mockk<UserRepository>()
            val database = mockk<Database>()
            val user =
                User(
                    id = UserId(Uuid.parse("00000000-0000-0000-0000-000000000001")),
                    authIdentity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-1")),
                    displayName = DisplayName("Alice"),
                )
            val expected = CatalogItems(emptyList())
            val query = "milk"
            val limit = 20
            val session =
                MindstockSession(
                    identity = user.authIdentity,
                    userId = user.id,
                    exp = Instant.fromEpochMilliseconds(Long.MAX_VALUE),
                    callId = Uuid.random(),
                )

            every { catalogItemService.search(query, limit) } returns expected

            mockkStatic("net.brightroom.mindstock.configuration.transaction.TransactionKt")
            coEvery {
                net.brightroom.mindstock.configuration.transaction
                    .tx<CatalogItems>(any(), any(), any())
            } coAnswers {
                val block = arg<suspend () -> RpcResult<CatalogItems, RpcError>>(2)
                block()
            }

            val impl =
                CatalogController(
                    catalogItemService = catalogItemService,
                    catalogItemRegisterService = catalogItemRegisterService,
                    catalogItemRepository = catalogItemRepository,
                    userRepository = userRepository,
                    session = session,
                    database = database,
                )
            impl.search(query, limit) shouldBe RpcResult.Ok(expected)
        }
    })
