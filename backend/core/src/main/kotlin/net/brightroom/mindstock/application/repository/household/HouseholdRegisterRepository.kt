package net.brightroom.mindstock.application.repository.household

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

interface HouseholdRegisterRepository {
    /** households + 初回 household_name + owner の 所属 event を INSERT(Household.create 相当の永続化)。 */
    fun registerHousehold(household: Household)

    /** household_name を 1 行 append(rename)。 */
    fun appendHouseholdName(
        householdId: HouseholdId,
        name: HouseholdName,
    )

    /** 所属 event を append(join)。 */
    fun joinMember(
        householdId: HouseholdId,
        resident: Resident,
        role: HouseholdMemberRole,
    )

    /** 所属 + 新 role event を append(changeRole)。 */
    fun changeMemberRole(
        householdId: HouseholdId,
        residentId: ResidentId,
        role: HouseholdMemberRole,
    )

    /** 除外 tombstone event を append(leave / removeMember。DELETE しない)。 */
    fun removeMember(
        householdId: HouseholdId,
        residentId: ResidentId,
    )
}
