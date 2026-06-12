package net.brightroom.mindstock.domain.model.household

import io.kotest.assertions.throwables.shouldThrow
import net.brightroom.mindstock.domain.exception.OwnerRequiredException
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.ResidentProfile
import kotlin.test.Test

class HouseholdRequireCanManageTest {
    private val owner = Resident(ResidentId.create(), ResidentProfile(DisplayName("世帯主")))
    private val member = Resident(ResidentId.create(), ResidentProfile(DisplayName("メンバー")))
    private val household =
        Household.create(HouseholdName("我が家"), owner).join(member, HouseholdMemberRole.メンバー)

    @Test
    fun メンバーは世帯管理権限が無く例外() {
        shouldThrow<OwnerRequiredException> { household.requireCanManage(member.id) }
    }

    @Test
    fun 世帯主は世帯管理権限を持つ() {
        household.requireCanManage(owner.id) // 例外が出なければ合格
    }
}
