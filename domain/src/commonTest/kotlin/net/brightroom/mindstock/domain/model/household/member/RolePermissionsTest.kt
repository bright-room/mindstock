package net.brightroom.mindstock.domain.model.household.member

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class RolePermissionsTest {
    @Test
    fun owner_can_manage_household() {
        RolePermissions.allows(HouseholdMemberRole.世帯主, HouseholdCapability.世帯管理) shouldBe true
    }

    @Test
    fun member_can_edit_inventory_but_not_manage_household() {
        RolePermissions.allows(HouseholdMemberRole.メンバー, HouseholdCapability.在庫編集) shouldBe true
        RolePermissions.allows(HouseholdMemberRole.メンバー, HouseholdCapability.世帯管理) shouldBe false
    }

    @Test
    fun viewer_can_do_nothing() {
        RolePermissions.allows(HouseholdMemberRole.閲覧者, HouseholdCapability.在庫編集) shouldBe false
    }
}
