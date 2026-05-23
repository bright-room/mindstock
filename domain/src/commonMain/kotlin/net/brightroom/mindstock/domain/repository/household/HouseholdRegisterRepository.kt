package net.brightroom.mindstock.domain.repository.household

import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.HouseholdMembershipId
import net.brightroom.mindstock.domain.model.user.UserId

public interface HouseholdRegisterRepository {
    public fun create(id: HouseholdId)
    public fun join(householdId: HouseholdId, userId: UserId, role: HouseholdMemberRole)
    public fun revoke(membershipId: HouseholdMembershipId)
}
