package net.brightroom.mindstock.frontend.feature.household

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.HouseholdProfile
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.member.Members
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.frontend.app.AuthFlow
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.rpc.household.InvitationPreview
import net.brightroom.mindstock.rpc.result.RpcError
import kotlin.test.Test

private class FakeAuthFlow : AuthFlow {
    var enteredId: HouseholdId? = null

    override suspend fun onResidentRegistered(resident: Resident) {}

    override suspend fun enterApp(activeId: HouseholdId) {
        enteredId = activeId
    }

    override fun needHousehold() {}

    override fun switchActiveHousehold(id: HouseholdId) {}

    override suspend fun refreshHouseholds() {}

    override fun applyDisplayName(name: DisplayName) {}

    override suspend fun leaveActiveHousehold() {}
}

private fun household() = Household(HouseholdId.create(), HouseholdProfile(HouseholdName("家")), Members(emptyList()))

private fun vm(
    create: suspend (HouseholdName) -> RpcOutcome<Household> = { RpcOutcome.Success(household()) },
    preview: suspend (InvitationCode) -> RpcOutcome<InvitationPreview> = {
        RpcOutcome.Success(InvitationPreview(HouseholdName("ゆいの家"), HouseholdMemberRole.メンバー))
    },
    join: suspend (InvitationCode) -> RpcOutcome<Household> = { RpcOutcome.Success(household()) },
    flow: AuthFlow = FakeAuthFlow(),
    toast: ToastController = ToastController(),
    reauth: ReauthController = ReauthController(),
) = NeedHouseholdViewModel(create, preview, join, flow, toast, reauth)

class NeedHouseholdViewModelTest {
    @Test
    fun create_enters_app() =
        runTest {
            val flow = FakeAuthFlow()
            val h = household()
            val v = vm(create = { RpcOutcome.Success(h) }, flow = flow)
            v.create("わたしの家")
            flow.enteredId shouldBe h.id
        }

    @Test
    fun preview_invalid_code_sets_error_without_calling_service() =
        runTest {
            val v = vm()
            v.preview("zzz")
            v.state.value.previewError
                .shouldNotBeNull()
            v.state.value.preview
                .shouldBeNull()
        }

    @Test
    fun preview_not_found_sets_error() =
        runTest {
            val v = vm(preview = { RpcOutcome.Failure(RpcError.NotFound("no")) })
            v.preview(InvitationCode.generate().toString())
            v.state.value.previewError
                .shouldNotBeNull()
            v.state.value.preview
                .shouldBeNull()
        }

    @Test
    fun preview_success_sets_preview() =
        runTest {
            val v = vm()
            v.preview(InvitationCode.generate().toString())
            v.state.value.preview
                .shouldNotBeNull()
        }

    @Test
    fun join_enters_app() =
        runTest {
            val flow = FakeAuthFlow()
            val h = household()
            val v = vm(join = { RpcOutcome.Success(h) }, flow = flow)
            v.join(InvitationCode.generate().toString())
            flow.enteredId shouldBe h.id
        }
}
