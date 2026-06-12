package net.brightroom.mindstock.application.service.invitation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import net.brightroom.mindstock.application.repository.invitation.InvitationRepository
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole

class InvitationServiceTest :
    FunSpec({
        val repository = mockk<InvitationRepository>()
        val service = InvitationService(repository)

        test("findByCode は repository の結果を返す") {
            val code = InvitationCode("ABCDEF")
            val invitation = Invitation.issue(HouseholdId.create(), HouseholdMemberRole.メンバー)
            every { repository.findByCode(code) } returns invitation
            service.findByCode(code) shouldBe invitation
        }
    })
