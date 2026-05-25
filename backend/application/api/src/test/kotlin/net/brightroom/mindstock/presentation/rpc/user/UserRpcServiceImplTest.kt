package net.brightroom.mindstock.presentation.rpc.user

import io.kotest.core.spec.style.FunSpec
import io.ktor.server.application.ApplicationCall
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import net.brightroom.mindstock.application.usecase.user.RenameUserHandler
import net.brightroom.mindstock.configuration.auth.actor
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.domain.repository.user.UserRepository
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class UserRpcServiceImplTest :
    FunSpec({
        afterTest { unmockkAll() }

        test("rename resolves actor via ApplicationCall and delegates to handler") {
            val handler = mockk<RenameUserHandler>(relaxed = true)
            val userRepository = mockk<UserRepository>()
            val call = mockk<ApplicationCall>()
            val user =
                User(
                    id = UserId(Uuid.parse("00000000-0000-0000-0000-000000000001")),
                    authIdentity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-1")),
                    displayName = DisplayName("Alice"),
                )

            mockkStatic("net.brightroom.mindstock.configuration.auth.ActorResolverKt")
            every { call.actor(userRepository) } returns user

            val impl = UserRpcServiceImpl(handler, userRepository, call)
            val newName = DisplayName("Bob")
            impl.rename(newName)

            verify { handler.handle(user, newName) }
        }
    })
