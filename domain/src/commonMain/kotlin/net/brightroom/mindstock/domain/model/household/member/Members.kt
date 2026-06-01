package net.brightroom.mindstock.domain.model.household.member

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

@Serializable
data class HouseholdMember(
    val resident: Resident,
    val role: HouseholdMemberRole,
)

@Serializable
data class Members(
    val list: List<HouseholdMember>,
) {
    fun size(): Int = list.size

    fun owner(): Resident = list.first { it.role == HouseholdMemberRole.世帯主 }.resident

    fun activeMembers(): List<Resident> = list.map { it.resident }

    fun contains(residentId: ResidentId): Boolean = list.any { it.resident.id == residentId }

    fun roleOf(residentId: ResidentId): HouseholdMemberRole =
        list.firstOrNull { it.resident.id == residentId }?.role
            ?: throw ResourceNotFoundException("member not found: $residentId")
}
