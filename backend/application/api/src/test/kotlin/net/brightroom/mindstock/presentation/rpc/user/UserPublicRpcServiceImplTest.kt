package net.brightroom.mindstock.presentation.rpc.user

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import net.brightroom.mindstock.application.service.user.UserRegisterService
import net.brightroom.mindstock.configuration.auth.MindstockPrincipal
import net.brightroom.mindstock.configuration.error.UnauthorizedException
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class UserPublicRpcServiceImplTest :
    FunSpec({
        afterTest { unmockkAll() }

        test("register pulls AuthIdentity from the call's Principal and delegates to UserRegisterService") {
            val userRegisterService = mockk<UserRegisterService>()
            val database = mockk<Database>()
            val call = mockk<ApplicationCall>()
            val displayName = DisplayName("Alice")
            val authIdentity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-1"))
            val expected =
                User(
                    id = UserId(Uuid.parse("00000000-0000-0000-0000-000000000001")),
                    authIdentity = authIdentity,
                    displayName = displayName,
                )

            mockkStatic("io.ktor.server.auth.AuthenticationKt")
            every { call.principal<MindstockPrincipal>() } returns MindstockPrincipal(authIdentity)
            every { userRegisterService.register(authIdentity, displayName) } returns expected

            mockkStatic("net.brightroom.mindstock.configuration.transaction.TransactionKt")
            coEvery {
                net.brightroom.mindstock.configuration.transaction
                    .tx<Any?>(any(), any())
            } coAnswers {
                @Suppress("UNCHECKED_CAST")
                val block = arg<suspend () -> Any?>(1)
                block()
            }

            val impl = UserPublicRpcServiceImpl(userRegisterService, call, database)
            impl.register(displayName) shouldBe expected
        }

        test("register throws UnauthorizedException when Principal is missing") {
            val userRegisterService = mockk<UserRegisterService>()
            val database = mockk<Database>()
            val call = mockk<ApplicationCall>()

            mockkStatic("io.ktor.server.auth.AuthenticationKt")
            every { call.principal<MindstockPrincipal>() } returns null

            val impl = UserPublicRpcServiceImpl(userRegisterService, call, database)
            shouldThrow<UnauthorizedException> {
                impl.register(DisplayName("Anyone"))
            }
        }
    })
