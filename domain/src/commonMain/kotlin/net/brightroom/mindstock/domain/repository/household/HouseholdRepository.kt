package net.brightroom.mindstock.domain.repository.household

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdMembership
import net.brightroom.mindstock.domain.model.user.UserId

public interface HouseholdRepository {
    public fun findById(id: HouseholdId): Household?

    /** ユーザーが現在所属する世帯のメンバーシップ(有効な最新)。未所属または revoke 済みなら null。 */
    public fun findMembershipOf(userId: UserId): HouseholdMembership?

    /** 世帯の有効なメンバー一覧。 */
    public fun listMembersOf(householdId: HouseholdId): List<HouseholdMembership>
}
