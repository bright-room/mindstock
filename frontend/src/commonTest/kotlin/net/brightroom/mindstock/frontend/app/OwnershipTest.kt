package net.brightroom.mindstock.frontend.app

import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.Households
import net.brightroom.mindstock.domain.model.household.member.HouseholdMember
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.member.Members
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.ResidentProfile
import kotlin.test.Test

private fun resident(id: ResidentId) = Resident(id, ResidentProfile(DisplayName("name")))

class OwnershipTest {
    @Test
    fun owner_member_is_owner() {
        val me = ResidentId.create()
        val hh = Household.create(HouseholdName("家"), resident(me))
        isOwner(Households(listOf(hh)), hh.id, me) shouldBe true
    }

    @Test
    fun non_owner_member_is_not_owner() {
        val owner = ResidentId.create()
        val me = ResidentId.create()
        val base = Household.create(HouseholdName("家"), resident(owner))
        val hh = base.copy(members = Members(base.members.list + HouseholdMember(resident(me), HouseholdMemberRole.メンバー)))
        isOwner(Households(listOf(hh)), hh.id, me) shouldBe false
    }

    @Test
    fun missing_session_is_not_owner() {
        isOwner(null, null, null) shouldBe false
    }

    @Test
    fun unknown_household_is_not_owner() {
        val me = ResidentId.create()
        val hh = Household.create(HouseholdName("家"), resident(me))
        isOwner(
            Households(listOf(hh)),
            net.brightroom.mindstock.domain.model.household.HouseholdId
                .create(),
            me,
        ) shouldBe false
    }
}
