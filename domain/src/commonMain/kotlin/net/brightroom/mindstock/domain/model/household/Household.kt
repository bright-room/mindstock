package net.brightroom.mindstock.domain.model.household

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.exception.OwnerRequiredException
import net.brightroom.mindstock.domain.model.household.member.HouseholdCapability
import net.brightroom.mindstock.domain.model.household.member.HouseholdMember
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.member.Members
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
