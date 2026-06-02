@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.household

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.Profile
import net.brightroom.mindstock.domain.model.household.member.HouseholdMember
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.member.Members
import net.brightroom.mindstock.domain.model.resident.Resident

/** Members を組み立てる(member ごとに hydrate 済み Resident を渡す)。 */
internal fun assembleHousehold(
    id: HouseholdId,
    name: HouseholdName,
    members: List<HouseholdMember>,
): Household = Household(id, Profile(name), Members(members))

internal fun member(
    resident: Resident,
    role: HouseholdMemberRole,
): HouseholdMember = HouseholdMember(resident, role)
