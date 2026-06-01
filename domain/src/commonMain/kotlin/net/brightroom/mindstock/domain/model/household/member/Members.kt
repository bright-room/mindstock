package net.brightroom.mindstock.domain.model.household.member

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

@Serializable
data class Members(
    val list: List<HouseholdMember>,
) {
    fun size(): Int = list.size

    fun owner(): Resident =
        list.firstOrNull { it.role == HouseholdMemberRole.世帯主 }?.resident
            ?: throw ResourceNotFoundException("owner not found")

    fun contains(residentId: ResidentId): Boolean = list.any { it.resident.id == residentId }

    fun roleOf(residentId: ResidentId): HouseholdMemberRole =
        list.firstOrNull { it.resident.id == residentId }?.role
            ?: throw ResourceNotFoundException("member not found: $residentId")

    fun add(member: HouseholdMember): Members = Members(list + member)

    fun changeRole(
        target: ResidentId,
        role: HouseholdMemberRole,
    ): Members = Members(list.map { if (it.resident.id == target) HouseholdMember(it.resident, role) else it })

    fun remove(target: ResidentId): Members = Members(list.filterNot { it.resident.id == target })
}
