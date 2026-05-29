package net.brightroom.mindstock.domain.model.household

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile
import kotlin.test.Test
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class HouseholdMembersTest {
    private fun profile(name: String) =
        Profile(
            userId = UserId(Uuid.generateV7()),
            displayName = DisplayName(name),
        )

    @Test
    fun `owner returns the OWNER profile`() {
        val ownerProfile = profile("alice")
        val memberProfile = profile("bob")
        val members =
            HouseholdMembers(
                listOf(
                    HouseholdMember(ownerProfile, HouseholdMemberRole.OWNER),
                    HouseholdMember(memberProfile, HouseholdMemberRole.MEMBER),
                ),
            )
        members.owner() shouldBe ownerProfile
    }

    @Test
    fun `owner returns null when no OWNER exists`() {
        val p = profile("bob")
        val members = HouseholdMembers(listOf(HouseholdMember(p, HouseholdMemberRole.MEMBER)))
        members.owner().shouldBeNull()
    }

    @Test
    fun `contains returns true when user is a member`() {
        val p = profile("alice")
        val members = HouseholdMembers(listOf(HouseholdMember(p, HouseholdMemberRole.OWNER)))
        members.contains(p.userId).shouldBeTrue()
    }

    @Test
    fun `contains returns false when user is not a member`() {
        val p1 = profile("alice")
        val p2 = profile("bob")
        val members = HouseholdMembers(listOf(HouseholdMember(p1, HouseholdMemberRole.OWNER)))
        members.contains(p2.userId).shouldBeFalse()
    }
}
