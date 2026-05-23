package net.brightroom.mindstock.domain.repository.household

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.user.User

interface HouseholdRegisterRepository {
    /** households + 初回 household_memberships(OWNER)を 1 トランザクションで INSERT。 */
    fun create(owner: User): Household

    /** household_memberships に行を INSERT。 */
    fun invite(household: Household, user: User, role: HouseholdMemberRole)

    /** household_membership_revocations に行を INSERT。 */
    fun revoke(household: Household, user: User)
}
