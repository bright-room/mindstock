package net.brightroom.mindstock.presentation.rpc.user

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import net.brightroom.mindstock.application.usecase.user.RegisterUserHandler
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

        test("register delegates to RegisterUserHandler") {
            val handler = mockk<RegisterUserHandler>()
            val database = mockk<Database>()
            val displayName = DisplayName("Alice")
            val authIdentity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-1"))
            val expected =
                User(
                    id = UserId(Uuid.parse("00000000-0000-0000-0000-000000000001")),
                    authIdentity = authIdentity,
                    displayName = displayName,
                )
            every { handler.handle(authIdentity, displayName) } returns expected

            mockkStatic("net.brightroom.mindstock.configuration.transaction.TransactionKt")
            coEvery {
                net.brightroom.mindstock.configuration.transaction
                    .tx<Any?>(any(), any())
            } coAnswers {
                @Suppress("UNCHECKED_CAST")
                val block = arg<suspend () -> Any?>(1)
                block()
            }

            val impl = UserPublicRpcServiceImpl(handler, database)
            impl.register(displayName, authIdentity) shouldBe expected
        }
    })
