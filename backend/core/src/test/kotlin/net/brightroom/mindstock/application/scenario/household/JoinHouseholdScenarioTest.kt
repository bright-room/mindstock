package net.brightroom.mindstock.application.scenario.household

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.brightroom.mindstock.application.service.household.HouseholdRegisterService
import net.brightroom.mindstock.application.service.invitation.InvitationService
import net.brightroom.mindstock.application.service.resident.ResidentService
import net.brightroom.mindstock.domain.exception.InvitationInvalidException
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.domain.model.household.invitation.InvitationValidity
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile

class JoinHouseholdScenarioTest :
    FunSpec({
        val invitationService = mockk<InvitationService>()
        val residentService = mockk<ResidentService>()
        val householdRegisterService = mockk<HouseholdRegisterService>(relaxed = true)
        val scenario = JoinHouseholdScenario(invitationService, residentService, householdRegisterService)

        val code = InvitationCode("ABCDEF")
        val joiner = Resident(ResidentId.create(), Profile(DisplayName("参加者")))
        val householdId = HouseholdId.create()

        test("無効な招待コードでは参加できず InvitationInvalidException(join を呼ばない)") {
            every { invitationService.findByCode(code) } returns
                Invitation(householdId, code, HouseholdMemberRole.メンバー, InvitationValidity.無効)
            shouldThrow<InvitationInvalidException> { scenario.run(code, joiner.id) }
            verify(exactly = 0) { householdRegisterService.join(any(), any(), any()) }
        }

        test("有効な招待コードで世帯に参加する") {
            every { invitationService.findByCode(code) } returns
                Invitation(householdId, code, HouseholdMemberRole.メンバー, InvitationValidity.有効)
            every { residentService.me(joiner.id) } returns joiner
            val joined = Household.create(HouseholdName("我が家"), joiner)
            every { householdRegisterService.join(householdId, joiner, HouseholdMemberRole.メンバー) } returns joined
            scenario.run(code, joiner.id) shouldBe joined
        }
    })
