package net.brightroom.mindstock.domain.model.household

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import net.brightroom.mindstock.domain.exception.MembershipRequiredException
import net.brightroom.mindstock.domain.exception.OwnerRequiredException
import net.brightroom.mindstock.domain.model.household.member.HouseholdMember
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.member.Members
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.ResidentProfile
import kotlin.test.Test

class HouseholdTest {
    private val ownerId = ResidentId.create()
    private val memberId = ResidentId.create()
    private val viewerId = ResidentId.create()

    private fun resident(
        id: ResidentId,
        name: String,
    ) = Resident(id, ResidentProfile(DisplayName(name)))

    private fun household(): Household =
        Household(
            HouseholdId.create(),
            HouseholdProfile(HouseholdName("わが家")),
            Members(
                listOf(
                    HouseholdMember(resident(ownerId, "おや"), HouseholdMemberRole.世帯主),
                    HouseholdMember(resident(memberId, "こ"), HouseholdMemberRole.メンバー),
                    HouseholdMember(resident(viewerId, "みる"), HouseholdMemberRole.閲覧者),
                ),
            ),
        )

    @Test
    fun requireCanManageMaster_世帯主は通る() {
        shouldNotThrowAny { household().requireCanManageMaster(ownerId) }
    }

    @Test
    fun requireCanManageMaster_メンバーはオーナー権限不足で弾く() {
        shouldThrow<OwnerRequiredException> { household().requireCanManageMaster(memberId) }
    }

    @Test
    fun requireCanManageMaster_閲覧者はオーナー権限不足で弾く() {
        shouldThrow<OwnerRequiredException> { household().requireCanManageMaster(viewerId) }
    }

    @Test
    fun requireCanManageMaster_非メンバーはメンバー必須で弾く() {
        shouldThrow<MembershipRequiredException> { household().requireCanManageMaster(ResidentId.create()) }
    }
}
