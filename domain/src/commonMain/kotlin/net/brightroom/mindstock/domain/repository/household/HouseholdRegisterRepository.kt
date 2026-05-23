package net.brightroom.mindstock.domain.repository.household

import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.HouseholdMembershipId
import net.brightroom.mindstock.domain.model.user.UserId

interface HouseholdRegisterRepository {
    fun create(id: HouseholdId)

    fun join(
        householdId: HouseholdId,
        userId: UserId,
        role: HouseholdMemberRole,
    )

    fun revoke(membershipId: HouseholdMembershipId)
}
