package net.brightroom.mindstock.domain.model.household.member

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.resident.Resident

@Serializable
data class HouseholdMember(
    val resident: Resident,
    val role: HouseholdMemberRole,
)
