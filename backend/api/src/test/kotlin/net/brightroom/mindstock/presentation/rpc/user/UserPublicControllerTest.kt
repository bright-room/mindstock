package net.brightroom.mindstock.presentation.rpc.user

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
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
import net.brightroom.mindstock.domain.model.user.profile.Profile
import net.brightroom.mindstock.rpc.RpcResult
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class UserPublicControllerTest :
    FunSpec({
        afterTest { unmockkAll() }

        test("register pulls AuthIdentity from the session and delegates to UserRegisterService") {
            val userRegisterService = mockk<UserRegisterService>()
            val displayName = DisplayName("Alice")
            val authIdentity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-1"))
            val expected =
                Profile(
                    userId = UserId(Uuid.parse("00000000-0000-0000-0000-000000000001")),
                    displayName = displayName,
                )
            val session =
                MindstockSession(
                    identity = authIdentity,
                    userId = null,
                    exp = Instant.fromEpochMilliseconds(Long.MAX_VALUE),
                    callId = Uuid.random(),
                )

            coEvery { userRegisterService.register(authIdentity, displayName) } returns expected

            val impl = UserPublicController(userRegisterService, session)
            runBlocking { impl.register(displayName) } shouldBe RpcResult.Ok(expected)
        }
    })
