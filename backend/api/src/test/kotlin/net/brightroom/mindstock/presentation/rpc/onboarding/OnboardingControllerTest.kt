package net.brightroom.mindstock.presentation.rpc.onboarding

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.datetime.Instant
import net.brightroom.mindstock.application.scenario.onboarding.RegisterFirstHouseholdScenario
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class OnboardingControllerTest :
    FunSpec({
        afterTest { unmockkAll() }

        test("register は session の AuthIdentity を使い Scenario に委譲する") {
            val scenario = mockk<RegisterFirstHouseholdScenario>()
            val database = mockk<Database>()
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

            every { scenario.run(authIdentity, displayName) } returns expected

            mockkStatic("net.brightroom.mindstock.configuration.transaction.TransactionKt")
            coEvery {
                net.brightroom.mindstock.configuration.transaction
                    .tx<Profile>(any(), any(), any())
            } coAnswers {
                val block = arg<suspend () -> RpcResult<Profile, RpcError>>(2)
                block()
            }

            val impl = OnboardingController(scenario, mockk(), mockk(), session, database)
            impl.register(displayName) shouldBe RpcResult.Ok(expected)
        }

        test("bootstrap は未登録(identity の User 無し)なら Unregistered を返す") {
            val scenario = mockk<RegisterFirstHouseholdScenario>()
            val userService = mockk<net.brightroom.mindstock.application.service.user.UserService>()
            val householdService = mockk<net.brightroom.mindstock.application.service.household.HouseholdService>()
            val database = mockk<Database>()
            val session =
                MindstockSession(
                    identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-1")),
                    userId = null,
                    exp = Instant.fromEpochMilliseconds(Long.MAX_VALUE),
                    callId = Uuid.random(),
                )

            every { userService.findByIdentity(any()) } throws
                net.brightroom.mindstock.domain.exception
                    .ResourceNotFoundException("not found")

            mockkStatic("net.brightroom.mindstock.configuration.transaction.TransactionKt")
            coEvery {
                net.brightroom.mindstock.configuration.transaction
                    .tx<net.brightroom.mindstock.rpc.SessionBootstrap>(any(), any(), any())
            } coAnswers {
                val block = arg<suspend () -> RpcResult<net.brightroom.mindstock.rpc.SessionBootstrap, RpcError>>(2)
                block()
            }

            val impl = OnboardingController(scenario, userService, householdService, session, database)
            impl.bootstrap() shouldBe RpcResult.Ok(net.brightroom.mindstock.rpc.SessionBootstrap.Unregistered)
        }
    })
