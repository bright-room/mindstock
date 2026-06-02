package net.brightroom.mindstock.application.repository.invitation

import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode

interface InvitationRepository {
    /** code でグローバルに解決(join 用)。不在は ResourceNotFoundException。 */
    fun findByCode(code: InvitationCode): Invitation
}
