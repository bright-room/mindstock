package net.brightroom.mindstock.domain.model.household.member

import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import kotlin.test.Test

class OwnerChangeabilityTest {
    private fun resident(name: String) = Resident(ResidentId.create(), Profile(DisplayName(name)))

    @Test
    fun sole_owner_is_not_changeable() {
        val owner = resident("おや")
        val members =
            Members(
                listOf(
                    HouseholdMember(owner, HouseholdMemberRole.世帯主),
                    HouseholdMember(resident("こ"), HouseholdMemberRole.メンバー),
                ),
            )
        OwnerChangeability.on(members, owner.id).allowed shouldBe false
    }

    @Test
    fun one_of_two_owners_is_changeable() {
        val owner1 = resident("おや1")
        val owner2 = resident("おや2")
        val members =
            Members(
                listOf(
                    HouseholdMember(owner1, HouseholdMemberRole.世帯主),
                    HouseholdMember(owner2, HouseholdMemberRole.世帯主),
                ),
            )
        OwnerChangeability.on(members, owner1.id).allowed shouldBe true
    }

    @Test
    fun non_owner_is_changeable() {
        val owner = resident("おや")
        val member = resident("こ")
        val members =
            Members(
                listOf(
                    HouseholdMember(owner, HouseholdMemberRole.世帯主),
                    HouseholdMember(member, HouseholdMemberRole.メンバー),
                ),
            )
        OwnerChangeability.on(members, member.id).allowed shouldBe true
    }
}
