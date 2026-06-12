@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package net.brightroom.mindstock.presentation.rpc.household

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.invitation.InvitationService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.HouseholdProfile
import net.brightroom.mindstock.domain.model.household.Households
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.invitation.InvitationValidity
import net.brightroom.mindstock.domain.model.household.member.HouseholdMember
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.member.Members
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.ResidentProfile
import net.brightroom.mindstock.rpc.household.InvitationPreview
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

class HouseholdControllerTest :
    FunSpec({
        val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub"))
        val residentId = ResidentId.create()
        val session =
            MindstockSession.Registered(
                identity,
                residentId,
                Clock.System.now().plus(1.hours),
                Uuid.random(),
            )

        val householdService = mockk<HouseholdService>()
        val invitationService = mockk<InvitationService>()
        val controller = HouseholdController(householdService, invitationService, session)

        test("list は HouseholdService.list(residentId) の結果を Ok で包んで返す") {
            val households = Households(emptyList())
            every { householdService.list(residentId) } returns households

            controller.list() shouldBe RpcResult.Ok(households)
        }

        test("previewInvite は招待コードから世帯名とロールを InvitationPreview として返す") {
            val householdId = HouseholdId.create()
            val invitation = Invitation.issue(householdId, HouseholdMemberRole.メンバー)
            val ownerResident =
                Resident(
                    ResidentId.create(),
                    ResidentProfile(DisplayName("ぬし")),
                )
            val household =
                Household(
                    householdId,
                    HouseholdProfile(HouseholdName("となりの家")),
                    Members(listOf(HouseholdMember(ownerResident, HouseholdMemberRole.世帯主))),
                )

            every { invitationService.findByCode(invitation.code) } returns invitation
            every { householdService.findById(householdId) } returns household

            controller.previewInvite(invitation.code) shouldBe
                RpcResult.Ok(InvitationPreview(HouseholdName("となりの家"), HouseholdMemberRole.メンバー))
        }
    })
