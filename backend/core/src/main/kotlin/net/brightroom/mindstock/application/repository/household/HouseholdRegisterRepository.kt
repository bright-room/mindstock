package net.brightroom.mindstock.application.repository.household

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.user.UserId

interface HouseholdRegisterRepository {
    /** households + 初回 household_memberships(OWNER)を 1 トランザクションで INSERT。 */
    fun create(
        ownerId: UserId,
        name: HouseholdName,
    ): Household

    /** household_memberships に行を INSERT。 */
    fun invite(
        household: Household,
        userId: UserId,
        role: HouseholdMemberRole,
    )

    /** household_membership_revocations に行を INSERT。 */
    fun revoke(
        household: Household,
        userId: UserId,
    )
}
