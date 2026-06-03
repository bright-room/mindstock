package net.brightroom.mindstock.application.scenario.invitation

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.invitation.InvitationRegisterService
import net.brightroom.mindstock.domain.exception.OwnerRequiredException
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile

class CreateInvitationScenarioTest :
    FunSpec({
        val householdService = mockk<HouseholdService>()
        val invitationRegisterService = mockk<InvitationRegisterService>(relaxed = true)
        val scenario = CreateInvitationScenario(householdService, invitationRegisterService)

        val owner = Resident(ResidentId.create(), Profile(DisplayName("世帯主")))
        val member = Resident(ResidentId.create(), Profile(DisplayName("メンバー")))
        val household = Household.create(HouseholdName("我が家"), owner).join(member, HouseholdMemberRole.メンバー)

        test("メンバーは招待を発行できず OwnerRequiredException(issue を呼ばない)") {
            every { householdService.findById(household.id) } returns household
            shouldThrow<OwnerRequiredException> {
                scenario.run(household.id, HouseholdMemberRole.メンバー, member.id)
            }
            verify(exactly = 0) { invitationRegisterService.issue(any()) }
        }

        test("世帯主は招待を発行できる") {
            every { householdService.findById(household.id) } returns household
            val issued = Invitation.issue(household.id, HouseholdMemberRole.メンバー)
            every { invitationRegisterService.issue(any()) } returns issued
            scenario.run(household.id, HouseholdMemberRole.メンバー, owner.id) shouldBe issued
        }
    })
