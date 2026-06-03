package net.brightroom.mindstock.application.service.invitation

import net.brightroom.mindstock.application.repository.invitation.InvitationRepository
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode

class InvitationService(
    private val invitationRepository: InvitationRepository,
) {
    fun findByCode(code: InvitationCode): Invitation = invitationRepository.findByCode(code)
}
