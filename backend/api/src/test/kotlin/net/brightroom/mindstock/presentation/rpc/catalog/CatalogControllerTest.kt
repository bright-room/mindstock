package net.brightroom.mindstock.presentation.rpc.catalog

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.application.ApplicationCall
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import net.brightroom.mindstock.application.repository.catalog.CatalogItemRepository
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.application.service.catalog.CatalogItemRegisterService
import net.brightroom.mindstock.application.service.catalog.CatalogItemService
import net.brightroom.mindstock.configuration.auth.actor
import net.brightroom.mindstock.domain.model.catalog.CatalogItems
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class CatalogControllerTest :
    FunSpec({
        afterTest { unmockkAll() }

        test("search resolves actor and delegates to CatalogItemService") {
            val catalogItemService = mockk<CatalogItemService>()
            val catalogItemRegisterService = mockk<CatalogItemRegisterService>()
            val catalogItemRepository = mockk<CatalogItemRepository>()
            val userRepository = mockk<UserRepository>()
            val call = mockk<ApplicationCall>()
            val database = mockk<Database>()
            val profile =
                Profile(
                    userId = UserId(Uuid.parse("00000000-0000-0000-0000-000000000001")),
                    displayName = DisplayName("Alice"),
                )
            val expected = CatalogItems(emptyList())
            val query = "milk"
            val limit = 20

            mockkStatic(ApplicationCall::actor)
            every { call.actor(userRepository) } returns profile
            every { catalogItemService.search(query, limit) } returns expected

            mockkStatic("net.brightroom.mindstock.configuration.transaction.TransactionKt")
            coEvery {
                net.brightroom.mindstock.configuration.transaction
                    .tx<Any?>(any(), any())
            } coAnswers {
                val block = arg<suspend () -> Any?>(1)
                block()
            }

            val impl =
                CatalogController(
                    catalogItemService = catalogItemService,
                    catalogItemRegisterService = catalogItemRegisterService,
                    catalogItemRepository = catalogItemRepository,
                    userRepository = userRepository,
                    call = call,
                    database = database,
                )
            impl.search(query, limit) shouldBe expected
        }
    })
