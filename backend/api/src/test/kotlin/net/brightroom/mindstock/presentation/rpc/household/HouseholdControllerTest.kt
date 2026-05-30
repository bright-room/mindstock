package net.brightroom.mindstock.presentation.rpc.household

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import net.brightroom.mindstock.application.service.household.HouseholdRegisterService
import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.user.UserService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdMembers
import net.brightroom.mindstock.domain.model.household.HouseholdName
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
class HouseholdControllerTest :
    FunSpec({
        afterTest { unmockkAll() }

        test("findOf resolves actor and delegates to HouseholdService") {
            val householdService = mockk<HouseholdService>()
            val householdRegisterService = mockk<HouseholdRegisterService>()
            val userService = mockk<UserService>()
            val database = mockk<Database>()
            val userId = UserId(Uuid.parse("00000000-0000-0000-0000-000000000001"))
            val household =
                Household(
                    id = HouseholdId(Uuid.parse("00000000-0000-0000-0000-000000000002")),
                    name = HouseholdName("テスト世帯"),
                    members = HouseholdMembers(emptyList()),
                )
            val session =
                MindstockSession(
                    identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-alice")),
                    userId = userId,
                    exp = Instant.fromEpochMilliseconds(Long.MAX_VALUE),
                    callId = Uuid.random(),
                )

            every { householdService.findOf(userId) } returns household

            mockkStatic("net.brightroom.mindstock.configuration.transaction.TransactionKt")
            coEvery {
                net.brightroom.mindstock.configuration.transaction
                    .tx<Household>(any(), any(), any())
            } coAnswers {
                val block = arg<suspend () -> RpcResult<Household, RpcError>>(2)
                block()
            }

            val impl =
                HouseholdController(
                    householdService = householdService,
                    householdRegisterService = householdRegisterService,
                    userService = userService,
                    session = session,
                    database = database,
                )
            runBlocking { impl.findOf() } shouldBe RpcResult.Ok(household)
        }
    })
