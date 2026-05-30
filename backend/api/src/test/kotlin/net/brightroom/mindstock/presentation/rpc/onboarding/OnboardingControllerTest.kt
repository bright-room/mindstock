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

            val impl = OnboardingController(scenario, session, database)
            impl.register(displayName) shouldBe RpcResult.Ok(expected)
        }
    })
