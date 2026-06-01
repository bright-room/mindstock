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

    fun owner(): Resident {
        val ownerMember =
            list.firstOrNull { it.role.is世帯主() }
                ?: throw ResourceNotFoundException("owner not found")
        return ownerMember.resident
    }

    fun contains(residentId: ResidentId): Boolean = list.any { it.resident.id == residentId }

    fun roleOf(residentId: ResidentId): HouseholdMemberRole {
        val member =
            list.firstOrNull { it.resident.id == residentId }
                ?: throw ResourceNotFoundException("member not found: $residentId")
        return member.role
    }

    fun add(member: HouseholdMember): Members = Members(list + member)

    fun changeRole(
        target: ResidentId,
        role: HouseholdMemberRole,
    ): Members {
        val changed =
            list.map { member ->
                if (member.resident.id == target) member.withRole(role) else member
            }
        return Members(changed)
    }

    fun remove(target: ResidentId): Members = Members(list.filterNot { it.resident.id == target })
}
