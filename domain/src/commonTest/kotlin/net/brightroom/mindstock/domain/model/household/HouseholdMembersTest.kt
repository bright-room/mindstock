package net.brightroom.mindstock.domain.model.household

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import kotlin.test.Test
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class HouseholdMembersTest {
    private fun user(name: String) = User(
        id = UserId(Uuid.generateV7()),
        authIdentity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-$name")),
        displayName = DisplayName(name),
    )

    @Test
    fun `owner returns the OWNER user`() {
        val ownerUser = user("alice")
        val memberUser = user("bob")
        val members = HouseholdMembers(listOf(
            HouseholdMember(ownerUser, HouseholdMemberRole.OWNER),
            HouseholdMember(memberUser, HouseholdMemberRole.MEMBER),
        ))
        members.owner() shouldBe ownerUser
    }

    @Test
    fun `owner returns null when no OWNER exists`() {
        val u = user("bob")
        val members = HouseholdMembers(listOf(HouseholdMember(u, HouseholdMemberRole.MEMBER)))
        members.owner().shouldBeNull()
    }

    @Test
    fun `contains returns true when user is a member`() {
        val u = user("alice")
        val members = HouseholdMembers(listOf(HouseholdMember(u, HouseholdMemberRole.OWNER)))
        members.contains(u).shouldBeTrue()
    }

    @Test
    fun `contains returns false when user is not a member`() {
        val u1 = user("alice")
        val u2 = user("bob")
        val members = HouseholdMembers(listOf(HouseholdMember(u1, HouseholdMemberRole.OWNER)))
        members.contains(u2).shouldBeFalse()
    }
}
