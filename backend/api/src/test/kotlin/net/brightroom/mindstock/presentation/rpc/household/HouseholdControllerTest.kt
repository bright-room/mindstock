package net.brightroom.mindstock.presentation.rpc.household

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.application.ApplicationCall
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.application.service.household.HouseholdRegisterService
import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.configuration.auth.actor
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdMembers
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile
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
            val householdRepository = mockk<HouseholdRepository>()
            val userRepository = mockk<UserRepository>()
            val call = mockk<ApplicationCall>()
            val database = mockk<Database>()
            val profile =
                Profile(
                    userId = UserId(Uuid.parse("00000000-0000-0000-0000-000000000001")),
                    displayName = DisplayName("Alice"),
                )
            val household =
                Household(
                    id = HouseholdId(Uuid.parse("00000000-0000-0000-0000-000000000002")),
                    members = HouseholdMembers(emptyList()),
                )

            mockkStatic(ApplicationCall::actor)
            every { call.actor(userRepository) } returns profile
            every { householdService.findOf(profile.userId) } returns household

            mockkStatic("net.brightroom.mindstock.configuration.transaction.TransactionKt")
            coEvery {
                net.brightroom.mindstock.configuration.transaction
                    .tx<Any?>(any(), any())
            } coAnswers {
                val block = arg<suspend () -> Any?>(1)
                block()
            }

            val impl =
                HouseholdController(
                    householdService = householdService,
                    householdRegisterService = householdRegisterService,
                    householdRepository = householdRepository,
                    userRepository = userRepository,
                    call = call,
                    database = database,
                )
            impl.findOf() shouldBe household
        }
    })
