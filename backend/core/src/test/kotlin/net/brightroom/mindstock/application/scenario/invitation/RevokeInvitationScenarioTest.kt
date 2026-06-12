package net.brightroom.mindstock.application.scenario.invitation

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.invitation.InvitationRegisterService
import net.brightroom.mindstock.application.service.invitation.InvitationService
import net.brightroom.mindstock.domain.exception.OwnerRequiredException
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.HouseholdProfile
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.member.HouseholdMember
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.member.Members
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.ResidentProfile

class RevokeInvitationScenarioTest :
    FunSpec({
        val invitationService = mockk<InvitationService>()
        val householdService = mockk<HouseholdService>()
        val invitationRegisterService = mockk<InvitationRegisterService>(relaxed = true)
        val scenario = RevokeInvitationScenario(invitationService, householdService, invitationRegisterService)

        val ownerId = ResidentId.create()
        val owner = Resident(ownerId, ResidentProfile(DisplayName("ぬし")))
        val memberId = ResidentId.create()
        val member = Resident(memberId, ResidentProfile(DisplayName("ひと")))
        val householdId = HouseholdId.create()
        val invitation = Invitation.issue(householdId, HouseholdMemberRole.メンバー)

        fun household(vararg pairs: Pair<Resident, HouseholdMemberRole>) =
            Household(
                householdId,
                HouseholdProfile(HouseholdName("わが家")),
                Members(pairs.map { (r, role) -> HouseholdMember(r, role) }),
            )

        beforeTest { clearMocks(invitationService, householdService, invitationRegisterService) }

        test("世帯主は招待を失効できる") {
            every { invitationService.findByCode(invitation.code) } returns invitation
            every { householdService.findById(householdId) } returns household(owner to HouseholdMemberRole.世帯主)
            scenario.run(invitation.code, ownerId)
            verify { invitationRegisterService.revoke(invitation.code) }
        }

        test("非世帯主メンバーは失効できず OwnerRequiredException で revoke しない") {
            every { invitationService.findByCode(invitation.code) } returns invitation
            every { householdService.findById(householdId) } returns
                household(owner to HouseholdMemberRole.世帯主, member to HouseholdMemberRole.メンバー)
            shouldThrow<OwnerRequiredException> { scenario.run(invitation.code, memberId) }
            verify(exactly = 0) { invitationRegisterService.revoke(invitation.code) }
        }
    })
