package net.brightroom.mindstock.domain.model.household

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.exception.LastOwnerException
import net.brightroom.mindstock.domain.exception.OwnerRequiredException
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.household.member.HouseholdCapability
import net.brightroom.mindstock.domain.model.household.member.HouseholdMember
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.member.Members
import net.brightroom.mindstock.domain.model.household.member.OwnerChangeability
import net.brightroom.mindstock.domain.model.household.member.RolePermissions
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

@Serializable
data class Household(
    val id: HouseholdId,
    val profile: Profile,
    val members: Members,
) {
    fun rename(
        name: HouseholdName,
        by: ResidentId,
    ): Household {
        requireCapability(by, HouseholdCapability.世帯管理)
        return Household(id, Profile(name), members)
    }

    fun join(
        resident: Resident,
        grantedRole: HouseholdMemberRole,
    ): Household =
        if (members.contains(resident.id)) {
            this
        } else {
            Household(id, profile, members.add(HouseholdMember(resident, grantedRole)))
        }

    fun changeRole(
        target: ResidentId,
        role: HouseholdMemberRole,
        by: ResidentId,
    ): Household {
        requireCapability(by, HouseholdCapability.世帯管理)
        if (!members.contains(target)) {
            throw ResourceNotFoundException("member not found: $target")
        }
        val changeability = OwnerChangeability.on(members, target)
        if (!role.is世帯主() && !changeability.allowed) {
            throw LastOwnerException("cannot demote last owner: $target")
        }
        return Household(id, profile, members.changeRole(target, role))
    }

    fun removeMember(
        target: ResidentId,
        by: ResidentId,
    ): Household {
        requireCapability(by, HouseholdCapability.世帯管理)
        if (!members.contains(target)) {
            throw ResourceNotFoundException("member not found: $target")
        }
        val changeability = OwnerChangeability.on(members, target)
        if (!changeability.allowed) {
            throw LastOwnerException("cannot remove last owner: $target")
        }
        return Household(id, profile, members.remove(target))
    }

    fun leave(by: ResidentId): Household {
        if (!members.contains(by)) {
            throw ResourceNotFoundException("member not found: $by")
        }
        val changeability = OwnerChangeability.on(members, by)
        if (!changeability.allowed) {
            throw LastOwnerException("last owner cannot leave: $by")
        }
        return Household(id, profile, members.remove(by))
    }

    private fun requireCapability(
        by: ResidentId,
        capability: HouseholdCapability,
    ) {
        if (!RolePermissions(members.roleOf(by), capability).isAllowed()) {
            throw OwnerRequiredException("$capability requires owner: $by")
        }
    }

    companion object {
        fun create(
            name: HouseholdName,
            owner: Resident,
        ): Household =
            Household(
                id = HouseholdId.create(),
                profile = Profile(name),
                members = Members(listOf(HouseholdMember(owner, HouseholdMemberRole.世帯主))),
            )
    }
}
