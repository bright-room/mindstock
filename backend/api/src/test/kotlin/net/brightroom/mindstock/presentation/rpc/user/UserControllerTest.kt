package net.brightroom.mindstock.presentation.rpc.user

import io.kotest.core.spec.style.FunSpec
import io.ktor.server.application.ApplicationCall
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.application.service.user.UserRegisterService
import net.brightroom.mindstock.configuration.auth.actor
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class UserControllerTest :
    FunSpec({
        afterTest { unmockkAll() }

        test("rename resolves actor via ApplicationCall and delegates to UserRegisterService") {
            val userRegisterService = mockk<UserRegisterService>(relaxed = true)
            val userRepository = mockk<UserRepository>()
            val call = mockk<ApplicationCall>()
            val database = mockk<Database>()
            val profile =
                Profile(
                    userId = UserId(Uuid.parse("00000000-0000-0000-0000-000000000001")),
                    displayName = DisplayName("Alice"),
                )

            mockkStatic(ApplicationCall::actor)
            every { call.actor(userRepository) } returns profile

            mockkStatic("net.brightroom.mindstock.configuration.transaction.TransactionKt")
            coEvery {
                net.brightroom.mindstock.configuration.transaction
                    .tx<Any?>(any(), any())
            } coAnswers {
                val block = arg<suspend () -> Any?>(1)
                block()
            }

            val impl = UserController(userRegisterService, userRepository, call, database)
            val newName = DisplayName("Bob")
            impl.rename(newName)

            verify { userRegisterService.rename(profile.userId, newName) }
        }
    })
