@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package net.brightroom.mindstock.presentation.rpc.household

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.brightroom.mindstock.application.scenario.household.JoinHouseholdScenario
import net.brightroom.mindstock.application.scenario.invitation.CreateInvitationScenario
import net.brightroom.mindstock.application.scenario.invitation.RevokeInvitationScenario
import net.brightroom.mindstock.application.service.household.HouseholdRegisterService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.HouseholdProfile
import net.brightroom.mindstock.domain.model.household.member.HouseholdMember
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.member.Members
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.ResidentProfile
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

class HouseholdRegisterControllerTest :
    FunSpec({
        val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub"))
        val residentId = ResidentId.create()
        val session =
            MindstockSession.Registered(
                identity,
                residentId,
                Clock.System.now().plus(1.hours),
                Uuid.random(),
            )

        val householdRegisterService = mockk<HouseholdRegisterService>(relaxed = true)
        val createInvitationScenario = mockk<CreateInvitationScenario>(relaxed = true)
        val revokeInvitationScenario = mockk<RevokeInvitationScenario>(relaxed = true)
        val joinHouseholdScenario = mockk<JoinHouseholdScenario>(relaxed = true)
        val controller =
            HouseholdRegisterController(
                householdRegisterService,
                createInvitationScenario,
                revokeInvitationScenario,
                joinHouseholdScenario,
                session,
            )

        fun buildHousehold(
            id: HouseholdId,
            name: String,
        ): Household {
            val ownerResident =
                Resident(
                    residentId,
                    ResidentProfile(DisplayName("ぬし")),
                )
            return Household(
                id,
                HouseholdProfile(HouseholdName(name)),
                Members(listOf(HouseholdMember(ownerResident, HouseholdMemberRole.世帯主))),
            )
        }

        test("create は HouseholdRegisterService.create の結果を Ok で包んで返す") {
            val householdId = HouseholdId.create()
            val name = HouseholdName("新居")
            val household = buildHousehold(householdId, "新居")
            every { householdRegisterService.create(name, residentId) } returns household

            controller.create(name) shouldBe RpcResult.Ok(household)
        }

        test("rename は HouseholdRegisterService.rename を呼び Ok(Unit) を返す") {
            val householdId = HouseholdId.create()
            val name = HouseholdName("改名")

            controller.rename(householdId, name) shouldBe RpcResult.Ok(Unit)

            verify(exactly = 1) { householdRegisterService.rename(householdId, name, residentId) }
        }
    })
