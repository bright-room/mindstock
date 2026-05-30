package net.brightroom.mindstock.application.scenario.onboarding

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.application.service.household.HouseholdRegisterService
import net.brightroom.mindstock.application.service.user.UserRegisterService
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class RegisterFirstHouseholdScenarioTest :
    FunSpec({

        val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-1"))
        val displayName = DisplayName("Alice")
        val profile =
            Profile(
                userId = UserId(Uuid.parse("00000000-0000-0000-0000-000000000001")),
                displayName = displayName,
            )

        test("未登録なら User を作り、デフォルト世帯名で Household を作って Profile を返す") {
            val userRepository = mockk<UserRepository>()
            val userRegisterService = mockk<UserRegisterService>()
            val householdRegisterService = mockk<HouseholdRegisterService>(relaxed = true)

            every { userRepository.findProfileByAuthIdentity(identity) } throws
                ResourceNotFoundException("user not found")
            every { userRegisterService.register(identity, displayName) } returns profile

            val scenario =
                RegisterFirstHouseholdScenario(userRepository, userRegisterService, householdRegisterService)

            scenario.run(identity, displayName) shouldBe profile

            val nameSlot = slot<HouseholdName>()
            verify(exactly = 1) { userRegisterService.register(identity, displayName) }
            verify(exactly = 1) { householdRegisterService.create(profile.userId, capture(nameSlot)) }
            nameSlot.captured() shouldBe "Aliceの家"
        }

        test("既に登録済みなら register も create も呼ばず既存 Profile を返す(冪等)") {
            val userRepository = mockk<UserRepository>()
            val userRegisterService = mockk<UserRegisterService>(relaxed = true)
            val householdRegisterService = mockk<HouseholdRegisterService>(relaxed = true)

            every { userRepository.findProfileByAuthIdentity(identity) } returns profile

            val scenario =
                RegisterFirstHouseholdScenario(userRepository, userRegisterService, householdRegisterService)

            scenario.run(identity, displayName) shouldBe profile

            verify(exactly = 0) { userRegisterService.register(any(), any()) }
            verify(exactly = 0) { householdRegisterService.create(any(), any()) }
        }
    })
