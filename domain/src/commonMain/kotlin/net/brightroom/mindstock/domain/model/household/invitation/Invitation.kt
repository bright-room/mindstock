package net.brightroom.mindstock.domain.model.household.invitation

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole

@Serializable
data class Invitation(
    val householdId: HouseholdId,
    val code: InvitationCode,
    val grantedRole: HouseholdMemberRole,
    val validity: InvitationValidity,
) {
    fun usable(): Boolean = validity == InvitationValidity.有効

    fun revoke(): Invitation = Invitation(householdId, code, grantedRole, InvitationValidity.無効)

    companion object {
        fun issue(
            householdId: HouseholdId,
            grantedRole: HouseholdMemberRole,
        ): Invitation = Invitation(householdId, InvitationCode.generate(), grantedRole, InvitationValidity.有効)
    }
}
