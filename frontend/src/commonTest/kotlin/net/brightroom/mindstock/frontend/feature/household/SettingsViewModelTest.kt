package net.brightroom.mindstock.frontend.feature.household

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.settings_error_last_owner_leave
import mindstock.frontend.generated.resources.settings_toast_invite_issued
import mindstock.frontend.generated.resources.settings_toast_member_removed
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.Households
import net.brightroom.mindstock.domain.model.household.Profile
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.domain.model.household.member.HouseholdMember
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.member.Members
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.frontend.app.AuthFlow
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.session.AppSession
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.rpc.result.RpcError
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

private class RecordingFlow : AuthFlow {
    var renamedDisplay: DisplayName? = null
    var refreshed: Boolean = false
    var left: Boolean = false
    var switched: HouseholdId? = null

    override suspend fun onResidentRegistered(resident: Resident) {}

    override suspend fun enterApp(activeId: HouseholdId) {}

    override fun needHousehold() {}

    override fun switchActiveHousehold(id: HouseholdId) {
        switched = id
    }

    override suspend fun refreshHouseholds() {
        refreshed = true
    }

    override fun applyDisplayName(name: DisplayName) {
        renamedDisplay = name
    }

    override suspend fun leaveActiveHousehold() {
        left = true
    }
}

private val meId = ResidentId.create()
private val otherId = ResidentId.create()
private val hid = HouseholdId.create()

private fun resident(
    id: ResidentId,
    name: String,
) = Resident(
    id,
    net.brightroom.mindstock.domain.model.resident.profile
        .Profile(DisplayName(name)),
)

private fun ownerHousehold() =
    Household(
        id = hid,
        profile = Profile(HouseholdName("我が家")),
        members =
            Members(
                listOf(
                    HouseholdMember(resident(meId, "わたし"), HouseholdMemberRole.世帯主),
                    HouseholdMember(resident(otherId, "ほか"), HouseholdMemberRole.メンバー),
                ),
            ),
    )

private fun session(): AppSession {
    val s = AppSession()
    s.setResident(meId, DisplayName("わたし"))
    s.setHouseholds(Households(listOf(ownerHousehold())), hid)
    return s
}

private fun anInvite() = Invitation.issue(hid, HouseholdMemberRole.メンバー)

private fun vm(
    session: AppSession = session(),
    renameDisplayName: suspend (DisplayName) -> RpcOutcome<Unit> = { RpcOutcome.Success(Unit) },
    renameHousehold: suspend (HouseholdId, HouseholdName) -> RpcOutcome<Unit> = { _, _ -> RpcOutcome.Success(Unit) },
    changeRole: suspend (HouseholdId, ResidentId, HouseholdMemberRole) -> RpcOutcome<Unit> = { _, _, _ ->
        RpcOutcome.Success(Unit)
    },
    removeMember: suspend (HouseholdId, ResidentId) -> RpcOutcome<Unit> = { _, _ -> RpcOutcome.Success(Unit) },
    leave: suspend (HouseholdId) -> RpcOutcome<Unit> = { RpcOutcome.Success(Unit) },
    createInvite: suspend (HouseholdId, HouseholdMemberRole) -> RpcOutcome<Invitation> = { _, _ ->
        RpcOutcome.Success(anInvite())
    },
    revokeInvite: suspend (InvitationCode) -> RpcOutcome<Unit> = { RpcOutcome.Success(Unit) },
    flow: AuthFlow = RecordingFlow(),
    toast: ToastController = ToastController(),
    reauth: ReauthController = ReauthController(),
) = SettingsViewModel(
    session = session,
    renameDisplayNameRpc = renameDisplayName,
    renameHouseholdRpc = renameHousehold,
    changeRoleRpc = changeRole,
    removeMemberRpc = removeMember,
    leaveRpc = leave,
    createInviteRpc = createInvite,
    revokeInviteRpc = revokeInvite,
    flow = flow,
    toast = toast,
    reauth = reauth,
)

class SettingsViewModelTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun state_derives_owner_and_members_from_session() =
        runTest {
            val v = vm()
            v.state.value.isOwner shouldBe true
            v.state.value.activeName shouldBe "我が家"
            v.state.value.members.size shouldBe 2
            v.state.value.displayName shouldBe "わたし"
        }

    @Test
    fun renameDisplayName_success_applies_to_flow() =
        runTest {
            val flow = RecordingFlow()
            val v = vm(flow = flow)
            v.renameDisplayName(DisplayName("あたらしい"))
            flow.renamedDisplay shouldBe DisplayName("あたらしい")
            // メンバー一覧の自分の名前を更新するため世帯を再取得する。
            flow.refreshed shouldBe true
        }

    @Test
    fun renameHousehold_success_refreshes() =
        runTest {
            val flow = RecordingFlow()
            val v = vm(flow = flow)
            v.renameHousehold(HouseholdName("新居"))
            flow.refreshed shouldBe true
        }

    @Test
    fun changeRole_success_refreshes() =
        runTest {
            val flow = RecordingFlow()
            val v = vm(flow = flow)
            v.changeRole(otherId, HouseholdMemberRole.世帯主)
            flow.refreshed shouldBe true
        }

    @Test
    fun removeMember_success_refreshes() =
        runTest {
            val flow = RecordingFlow()
            val v = vm(flow = flow)
            v.removeMember(otherId)
            flow.refreshed shouldBe true
        }

    @Test
    fun leave_success_calls_flow_leave() =
        runTest {
            val flow = RecordingFlow()
            val v = vm(flow = flow)
            v.leave()
            flow.left shouldBe true
        }

    @Test
    fun leave_conflict_does_not_call_flow_leave() =
        runTest {
            val flow = RecordingFlow()
            val v = vm(leave = { RpcOutcome.Failure(RpcError.Conflict("last owner cannot leave")) }, flow = flow)
            v.leave()
            flow.left shouldBe false
        }

    @Test
    fun leave_unauthorized_requests_reauth() =
        runTest {
            val flow = RecordingFlow()
            val reauth = ReauthController()
            var signals = 0
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { reauth.signal.collect { signals++ } }
            val v = vm(leave = { RpcOutcome.Failure(RpcError.Unauthorized("x")) }, flow = flow, reauth = reauth)
            v.leave()
            signals shouldBe 1
            flow.left shouldBe false
            job.cancel()
        }

    @Test
    fun leave_conflict_shows_last_owner_toast() =
        runTest {
            val flow = RecordingFlow()
            val toast = ToastController()
            val v = vm(leave = { RpcOutcome.Failure(RpcError.Conflict("last owner")) }, flow = flow, toast = toast)
            v.leave()
            val shown = toast.current.value
            shown.shouldNotBeNull()
            shown.text.resource shouldBe Res.string.settings_error_last_owner_leave
            flow.left shouldBe false
        }

    @Test
    fun createInvite_success_stores_in_state() =
        runTest {
            val v = vm()
            v.createInvite(HouseholdMemberRole.メンバー)
            v.state.value.issuedInvite
                .shouldNotBeNull()
        }

    @Test
    fun removeMember_success_shows_toast() =
        runTest {
            val toast = ToastController()
            val v = vm(toast = toast)
            v.removeMember(otherId)
            toast.current.value
                ?.text
                ?.resource shouldBe Res.string.settings_toast_member_removed
        }

    @Test
    fun createInvite_success_shows_toast() =
        runTest {
            val toast = ToastController()
            val v = vm(toast = toast)
            v.createInvite(HouseholdMemberRole.メンバー)
            toast.current.value
                ?.text
                ?.resource shouldBe Res.string.settings_toast_invite_issued
        }

    @Test
    fun revokeInvite_success_clears_state() =
        runTest {
            val v = vm()
            v.createInvite(HouseholdMemberRole.メンバー)
            v.state.value.issuedInvite
                .shouldNotBeNull()
            v.revokeInvite()
            v.state.value.issuedInvite
                .shouldBeNull()
        }

    @Test
    fun switchHousehold_delegates_to_flow() =
        runTest {
            val flow = RecordingFlow()
            val v = vm(flow = flow)
            v.switchHousehold(hid)
            flow.switched shouldBe hid
        }
}
