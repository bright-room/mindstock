package net.brightroom.mindstock.domain.repository.household

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.user.User

interface HouseholdRepository {
    /** ユーザーが所属する世帯(MVP は 1 ユーザー 1 世帯前提)。未所属なら null。 */
    fun findOf(user: User): Household?

    /** id 引き(主に RPC 経由)。 */
    fun findById(id: HouseholdId): Household?
}
