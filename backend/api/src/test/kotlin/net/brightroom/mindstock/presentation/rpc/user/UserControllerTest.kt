package net.brightroom.mindstock.presentation.rpc.user

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import net.brightroom.mindstock.application.service.user.UserRegisterService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.rpc.RpcResult
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class UserControllerTest :
    FunSpec({
        afterTest { unmockkAll() }

        test("rename resolves actor via session and delegates to UserRegisterService") {
            val userRegisterService = mockk<UserRegisterService>(relaxed = true)
            val userId = UserId(Uuid.parse("00000000-0000-0000-0000-000000000001"))
            val session =
                MindstockSession(
                    identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-alice")),
                    userId = userId,
                    exp = Instant.fromEpochMilliseconds(Long.MAX_VALUE),
                    callId = Uuid.random(),
                )

            val impl = UserController(userRegisterService, session)
            val newName = DisplayName("Bob")
            runBlocking { impl.rename(newName) } shouldBe RpcResult.Ok(Unit)

            coVerify { userRegisterService.rename(userId, newName) }
        }
    })
