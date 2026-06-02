package net.brightroom.mindstock.infrastructure.datasource.invitation

import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.domain.model.household.invitation.InvitationValidity
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole

internal fun assembleInvitation(
    householdId: HouseholdId,
    code: InvitationCode,
    grantedRole: HouseholdMemberRole,
    validity: InvitationValidity,
): Invitation = Invitation(householdId, code, grantedRole, validity)
