package net.brightroom.mindstock.domain.model.household.member

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import kotlin.test.Test

class MembersTest {
    private fun resident(name: String) = Resident(ResidentId.create(), Profile(DisplayName(name)))

    @Test
    fun ownerは世帯主の住人を返す() {
        val owner = resident("おや")
        val members =
            Members(
                listOf(
                    HouseholdMember(owner, HouseholdMemberRole.世帯主),
                    HouseholdMember(resident("こ"), HouseholdMemberRole.メンバー),
                ),
            )
        members.owner() shouldBe owner
    }

    @Test
    fun roleOfはメンバーの役割を返す() {
        val member = resident("こ")
        val members =
            Members(
                listOf(
                    HouseholdMember(resident("おや"), HouseholdMemberRole.世帯主),
                    HouseholdMember(member, HouseholdMemberRole.閲覧者),
                ),
            )
        members.roleOf(member.id) shouldBe HouseholdMemberRole.閲覧者
    }

    @Test
    fun roleOfは非メンバーで例外を投げる() {
        val members = Members(listOf(HouseholdMember(resident("おや"), HouseholdMemberRole.世帯主)))
        shouldThrow<ResourceNotFoundException> { members.roleOf(ResidentId.create()) }
    }
}
