package net.brightroom.mindstock.domain.model.household.member

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class RolePermissionsTest {
    @Test
    fun 世帯主は世帯管理ができる() {
        RolePermissions(HouseholdMemberRole.世帯主, HouseholdCapability.世帯管理).isAllowed() shouldBe true
    }

    @Test
    fun メンバーは在庫編集できるが世帯管理はできない() {
        RolePermissions(HouseholdMemberRole.メンバー, HouseholdCapability.在庫編集).isAllowed() shouldBe true
        RolePermissions(HouseholdMemberRole.メンバー, HouseholdCapability.世帯管理).isAllowed() shouldBe false
    }

    @Test
    fun 閲覧者は何もできない() {
        RolePermissions(HouseholdMemberRole.閲覧者, HouseholdCapability.在庫編集).isAllowed() shouldBe false
    }
}
