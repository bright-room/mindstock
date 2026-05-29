package net.brightroom.mindstock.presentation.rpc.user

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.datetime.Instant
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.application.service.user.UserRegisterService
import net.brightroom.mindstock.configuration.auth.MindstockSession
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
class UserControllerTest :
    FunSpec({
        afterTest { unmockkAll() }

        test("rename resolves actor via session and delegates to UserRegisterService") {
            val userRegisterService = mockk<UserRegisterService>(relaxed = true)
            val userRepository = mockk<UserRepository>()
            val database = mockk<Database>()
            val user =
                User(
                    id = UserId(Uuid.parse("00000000-0000-0000-0000-000000000001")),
                    authIdentity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-1")),
                    displayName = DisplayName("Alice"),
                )
            val session =
                MindstockSession(
                    identity = user.authIdentity,
                    userId = user.id,
                    exp = Instant.fromEpochMilliseconds(Long.MAX_VALUE),
                    callId = Uuid.random(),
                )
            every { userRepository.findById(user.id) } returns user

            mockkStatic("net.brightroom.mindstock.configuration.transaction.TransactionKt")
            coEvery {
                net.brightroom.mindstock.configuration.transaction
                    .tx<Unit>(any(), any(), any())
            } coAnswers {
                val block = arg<suspend () -> RpcResult<Unit, RpcError>>(2)
                block()
            }

            val impl = UserController(userRegisterService, userRepository, session, database)
            val newName = DisplayName("Bob")
            impl.rename(newName) shouldBe RpcResult.Ok(Unit)

            verify { userRegisterService.rename(user, newName) }
        }
    })
