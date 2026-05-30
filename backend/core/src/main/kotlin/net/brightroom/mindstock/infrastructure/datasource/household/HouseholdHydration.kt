package net.brightroom.mindstock.infrastructure.datasource.household

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdMember
import net.brightroom.mindstock.domain.model.household.HouseholdMembers
import net.brightroom.mindstock.domain.model.household.HouseholdName
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal fun hydrateHousehold(
    householdId: Uuid,
    name: HouseholdName,
    members: List<HouseholdMember>,
): Household =
    Household(
        id = HouseholdId(householdId),
        name = name,
        members = HouseholdMembers(members),
    )
