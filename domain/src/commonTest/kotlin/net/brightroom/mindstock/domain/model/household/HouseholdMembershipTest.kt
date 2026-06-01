package net.brightroom.mindstock.domain.model.household

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.exception.LastOwnerException
import net.brightroom.mindstock.domain.exception.OwnerRequiredException
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import kotlin.test.Test
import net.brightroom.mindstock.domain.model.resident.profile.Profile as ResidentProfile

class HouseholdMembershipTest {
    private fun resident(name: String) = Resident(ResidentId.create(), ResidentProfile(DisplayName(name)))

    private fun householdWithMember(): Triple<Household, Resident, Resident> {
        val owner = resident("おや")
        val member = resident("こ")
        val household =
            Household
                .create(HouseholdName("我が家"), owner)
                .join(member, HouseholdMemberRole.メンバー)
        return Triple(household, owner, member)
    }

    @Test
    fun join_applies_granted_role() {
        val (household, _, member) = householdWithMember()
        household.members.roleOf(member.id) shouldBe HouseholdMemberRole.メンバー
    }

    @Test
    fun owner_can_change_member_role() {
        val (household, owner, member) = householdWithMember()
        val updated = household.changeRole(member.id, HouseholdMemberRole.閲覧者, owner.id)
        updated.members.roleOf(member.id) shouldBe HouseholdMemberRole.閲覧者
    }

    @Test
    fun non_owner_cannot_change_role() {
        val (household, owner, member) = householdWithMember()
        shouldThrow<OwnerRequiredException> {
            household.changeRole(owner.id, HouseholdMemberRole.閲覧者, member.id)
        }
    }

    @Test
    fun demoting_last_owner_is_rejected() {
        val owner = resident("おや")
        val household = Household.create(HouseholdName("我が家"), owner)
        shouldThrow<LastOwnerException> {
            household.changeRole(owner.id, HouseholdMemberRole.メンバー, owner.id)
        }
    }

    @Test
    fun removing_last_owner_is_rejected() {
        val owner = resident("おや")
        val household = Household.create(HouseholdName("我が家"), owner)
        shouldThrow<LastOwnerException> { household.removeMember(owner.id, owner.id) }
    }

    @Test
    fun member_can_leave() {
        val (household, _, member) = householdWithMember()
        household.leave(member.id).members.contains(member.id) shouldBe false
    }

    @Test
    fun last_owner_cannot_leave() {
        val owner = resident("おや")
        val household = Household.create(HouseholdName("我が家"), owner)
        shouldThrow<LastOwnerException> { household.leave(owner.id) }
    }

    @Test
    fun changeRole_on_non_member_target_throws() {
        val (household, owner, _) = householdWithMember()
        shouldThrow<ResourceNotFoundException> {
            household.changeRole(ResidentId.create(), HouseholdMemberRole.閲覧者, owner.id)
        }
    }

    @Test
    fun removeMember_on_non_member_target_throws() {
        val (household, owner, _) = householdWithMember()
        shouldThrow<ResourceNotFoundException> {
            household.removeMember(ResidentId.create(), owner.id)
        }
    }

    @Test
    fun leave_by_non_member_throws() {
        val (household, _, _) = householdWithMember()
        shouldThrow<ResourceNotFoundException> { household.leave(ResidentId.create()) }
    }
}
