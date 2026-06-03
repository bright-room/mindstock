package net.brightroom.mindstock.application.service.invitation

import net.brightroom.mindstock.application.repository.invitation.InvitationRegisterRepository
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode

class InvitationRegisterService(
    private val invitationRegisterRepository: InvitationRegisterRepository,
) {
    /** 発行/再発行(owner 認可・household 整合は Scenario が担う)。code PK 衝突は repo がリトライ。 */
    fun issue(invitation: Invitation): Invitation = invitationRegisterRepository.issue(invitation)

    fun revoke(code: InvitationCode) = invitationRegisterRepository.revoke(code)
}
