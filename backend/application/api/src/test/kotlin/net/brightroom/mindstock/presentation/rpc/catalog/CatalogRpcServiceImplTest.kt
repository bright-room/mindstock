package net.brightroom.mindstock.presentation.rpc.catalog

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.application.ApplicationCall
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import net.brightroom.mindstock.application.usecase.catalog.FindCatalogItemByIdHandler
import net.brightroom.mindstock.application.usecase.catalog.RegisterCatalogItemHandler
import net.brightroom.mindstock.application.usecase.catalog.ReviseCatalogItemHandler
import net.brightroom.mindstock.application.usecase.catalog.SearchCatalogItemsHandler
import net.brightroom.mindstock.configuration.auth.actor
import net.brightroom.mindstock.domain.model.catalog.CatalogItems
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.domain.repository.catalog.CatalogItemRepository
import net.brightroom.mindstock.domain.repository.user.UserRepository
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class CatalogRpcServiceImplTest :
    FunSpec({
        afterTest { unmockkAll() }

        test("search resolves actor and delegates to SearchCatalogItemsHandler") {
            val searchHandler = mockk<SearchCatalogItemsHandler>()
            val findByIdHandler = mockk<FindCatalogItemByIdHandler>()
            val registerHandler = mockk<RegisterCatalogItemHandler>()
            val reviseHandler = mockk<ReviseCatalogItemHandler>()
            val catalogItemRepository = mockk<CatalogItemRepository>()
            val userRepository = mockk<UserRepository>()
            val call = mockk<ApplicationCall>()
            val user =
                User(
                    id = UserId(Uuid.parse("00000000-0000-0000-0000-000000000001")),
                    authIdentity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-1")),
                    displayName = DisplayName("Alice"),
                )
            val expected = CatalogItems(emptyList())
            val query = "milk"
            val limit = 20

            mockkStatic("net.brightroom.mindstock.configuration.auth.ActorResolverKt")
            every { call.actor(userRepository) } returns user
            every { searchHandler.handle(query, limit) } returns expected

            val impl =
                CatalogRpcServiceImpl(
                    searchHandler = searchHandler,
                    findByIdHandler = findByIdHandler,
                    registerHandler = registerHandler,
                    reviseHandler = reviseHandler,
                    catalogItemRepository = catalogItemRepository,
                    userRepository = userRepository,
                    call = call,
                )
            impl.search(query, limit) shouldBe expected
        }
    })
