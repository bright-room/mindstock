package net.brightroom.mindstock.application.scenario.invitation

import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.invitation.InvitationRegisterService
import net.brightroom.mindstock.application.service.invitation.InvitationService
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

class RevokeInvitationScenario(
    private val invitationService: InvitationService,
    private val householdService: HouseholdService,
    private val invitationRegisterService: InvitationRegisterService,
) {
    fun run(
        code: InvitationCode,
        actor: ResidentId,
    ) {
        val invitation = invitationService.findByCode(code)
        householdService.findById(invitation.householdId).requireCanManage(actor)
        invitationRegisterService.revoke(code)
    }
}
