package net.brightroom.mindstock.domain.model.household

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.exception.LastOwnerException
import net.brightroom.mindstock.domain.exception.OwnerRequiredException
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
        return copy(profile = Profile(name))
    }

    fun join(
        resident: Resident,
        grantedRole: HouseholdMemberRole,
    ): Household = copy(members = Members(members.list + HouseholdMember(resident, grantedRole)))

    fun changeRole(
        target: ResidentId,
        role: HouseholdMemberRole,
        by: ResidentId,
    ): Household {
        requireCapability(by, HouseholdCapability.世帯管理)
        if (role != HouseholdMemberRole.世帯主 && !OwnerChangeability.on(members, target).allowed) {
            throw LastOwnerException("cannot demote last owner: $target")
        }
        return copy(
            members =
                Members(
                    members.list.map { if (it.resident.id == target) it.copy(role = role) else it },
                ),
        )
    }

    fun removeMember(
        target: ResidentId,
        by: ResidentId,
    ): Household {
        requireCapability(by, HouseholdCapability.世帯管理)
        if (!OwnerChangeability.on(members, target).allowed) {
            throw LastOwnerException("cannot remove last owner: $target")
        }
        return copy(members = Members(members.list.filterNot { it.resident.id == target }))
    }

    fun leave(by: ResidentId): Household {
        if (!OwnerChangeability.on(members, by).allowed) {
            throw LastOwnerException("last owner cannot leave: $by")
        }
        return copy(members = Members(members.list.filterNot { it.resident.id == by }))
    }

    private fun requireCapability(
        by: ResidentId,
        capability: HouseholdCapability,
    ) {
        if (!RolePermissions.allows(members.roleOf(by), capability)) {
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
