package net.brightroom.mindstock.application.scenario.invitation

import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.invitation.InvitationRegisterService
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

class CreateInvitationScenario(
    private val householdService: HouseholdService,
    private val invitationRegisterService: InvitationRegisterService,
) {
    fun run(
        householdId: HouseholdId,
        role: HouseholdMemberRole,
        actor: ResidentId,
    ): Invitation {
        householdService.findById(householdId).requireCanManage(actor)
        return invitationRegisterService.issue(Invitation.issue(householdId, role))
    }
}
