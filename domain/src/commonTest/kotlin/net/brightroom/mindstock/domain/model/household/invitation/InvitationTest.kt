package net.brightroom.mindstock.domain.model.household.invitation

import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import kotlin.test.Test

class InvitationTest {
    @Test
    fun 取消した招待は使用不可になる() {
        val invitation = Invitation.issue(HouseholdId.create(), HouseholdMemberRole.メンバー)
        invitation.usable() shouldBe true
        invitation.revoke().usable() shouldBe false
    }
}
