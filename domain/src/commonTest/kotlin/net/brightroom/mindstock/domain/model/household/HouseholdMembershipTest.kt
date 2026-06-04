package net.brightroom.mindstock.domain.model.household

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.exception.LastOwnerException
import net.brightroom.mindstock.domain.exception.MembershipRequiredException
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
    fun 参加時に付与された役割が適用される() {
        val (household, _, member) = householdWithMember()
        household.members.roleOf(member.id) shouldBe HouseholdMemberRole.メンバー
    }

    @Test
    fun 世帯主はメンバーの役割を変更できる() {
        val (household, owner, member) = householdWithMember()
        val updated = household.changeRole(member.id, HouseholdMemberRole.閲覧者, owner.id)
        updated.members.roleOf(member.id) shouldBe HouseholdMemberRole.閲覧者
    }

    @Test
    fun 非世帯主は役割を変更できない() {
        val (household, owner, member) = householdWithMember()
        shouldThrow<OwnerRequiredException> {
            household.changeRole(owner.id, HouseholdMemberRole.閲覧者, member.id)
        }
    }

    @Test
    fun 最後の世帯主の降格は拒否される() {
        val owner = resident("おや")
        val household = Household.create(HouseholdName("我が家"), owner)
        shouldThrow<LastOwnerException> {
            household.changeRole(owner.id, HouseholdMemberRole.メンバー, owner.id)
        }
    }

    @Test
    fun 最後の世帯主の削除は拒否される() {
        val owner = resident("おや")
        val household = Household.create(HouseholdName("我が家"), owner)
        shouldThrow<LastOwnerException> { household.removeMember(owner.id, owner.id) }
    }

    @Test
    fun メンバーは退出できる() {
        val (household, _, member) = householdWithMember()
        household.leave(member.id).members.contains(member.id) shouldBe false
    }

    @Test
    fun 最後の世帯主は退出できない() {
        val owner = resident("おや")
        val household = Household.create(HouseholdName("我が家"), owner)
        shouldThrow<LastOwnerException> { household.leave(owner.id) }
    }

    @Test
    fun 非メンバーへの役割変更は例外を投げる() {
        val (household, owner, _) = householdWithMember()
        shouldThrow<ResourceNotFoundException> {
            household.changeRole(ResidentId.create(), HouseholdMemberRole.閲覧者, owner.id)
        }
    }

    @Test
    fun 非メンバーの削除は例外を投げる() {
        val (household, owner, _) = householdWithMember()
        shouldThrow<ResourceNotFoundException> {
            household.removeMember(ResidentId.create(), owner.id)
        }
    }

    @Test
    fun 非メンバーの退出は例外を投げる() {
        val (household, _, _) = householdWithMember()
        shouldThrow<ResourceNotFoundException> { household.leave(ResidentId.create()) }
    }

    @Test
    fun 既存メンバーの再参加は重複しない() {
        val owner = resident("おや")
        val member = resident("こ")
        val household =
            Household
                .create(HouseholdName("我が家"), owner)
                .join(member, HouseholdMemberRole.メンバー)
                .join(member, HouseholdMemberRole.閲覧者) // 2回目: 何も起きない
        household.members.size() shouldBe 2
        household.members.roleOf(member.id) shouldBe HouseholdMemberRole.メンバー // 役割も変わらない
    }

    @Test
    fun requireMember_メンバーは通過する() {
        val (household, _, member) = householdWithMember()
        shouldNotThrowAny { household.requireMember(member.id) }
    }

    @Test
    fun requireMember_非メンバーはMembershipRequiredExceptionを投げる() {
        val (household, _, _) = householdWithMember()
        shouldThrow<MembershipRequiredException> { household.requireMember(ResidentId.create()) }
    }
}
