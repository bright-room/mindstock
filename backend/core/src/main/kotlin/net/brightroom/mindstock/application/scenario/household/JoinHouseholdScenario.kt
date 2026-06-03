package net.brightroom.mindstock.application.scenario.household

import net.brightroom.mindstock.application.service.household.HouseholdRegisterService
import net.brightroom.mindstock.application.service.invitation.InvitationService
import net.brightroom.mindstock.application.service.resident.ResidentService
import net.brightroom.mindstock.domain.exception.InvitationInvalidException
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

class JoinHouseholdScenario(
    private val invitationService: InvitationService,
    private val residentService: ResidentService,
    private val householdRegisterService: HouseholdRegisterService,
) {
    fun run(
        code: InvitationCode,
        actor: ResidentId,
    ): Household {
        val invitation = invitationService.findByCode(code)
        if (!invitation.usable()) {
            throw InvitationInvalidException("invitation not usable: $code")
        }
        val resident = residentService.me(actor)
        return householdRegisterService.join(invitation.householdId, resident, invitation.grantedRole)
    }
}
