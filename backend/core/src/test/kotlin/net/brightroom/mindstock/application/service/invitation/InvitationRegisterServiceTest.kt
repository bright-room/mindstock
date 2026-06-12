package net.brightroom.mindstock.application.service.invitation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.brightroom.mindstock.application.repository.invitation.InvitationRegisterRepository
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole

class InvitationRegisterServiceTest :
    FunSpec({
        val repository = mockk<InvitationRegisterRepository>(relaxed = true)
        val service = InvitationRegisterService(repository)

        test("issue は repository.issue の結果を返す") {
            val invitation = Invitation.issue(HouseholdId.create(), HouseholdMemberRole.メンバー)
            every { repository.issue(invitation) } returns invitation
            service.issue(invitation) shouldBe invitation
        }

        test("revoke は repository.revoke に委譲する") {
            val code = InvitationCode("ABCDEF")
            service.revoke(code)
            verify { repository.revoke(code) }
        }
    })
