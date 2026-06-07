package net.brightroom.mindstock.frontend.feature.household

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.settings_error_last_owner_change_role
import mindstock.frontend.generated.resources.settings_error_last_owner_leave
import mindstock.frontend.generated.resources.settings_error_last_owner_remove
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.frontend.app.AuthFlow
import net.brightroom.mindstock.frontend.app.isOwner
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.errorText
import net.brightroom.mindstock.frontend.core.rpc.requiresReauth
import net.brightroom.mindstock.frontend.core.session.AppSession
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.frontend.core.ui.UiText
import net.brightroom.mindstock.rpc.result.RpcError
import org.jetbrains.compose.resources.StringResource

class SettingsViewModel(
    session: AppSession,
    private val renameDisplayNameRpc: suspend (DisplayName) -> RpcOutcome<Unit>,
    private val renameHouseholdRpc: suspend (HouseholdId, HouseholdName) -> RpcOutcome<Unit>,
    private val changeRoleRpc: suspend (HouseholdId, ResidentId, HouseholdMemberRole) -> RpcOutcome<Unit>,
    private val removeMemberRpc: suspend (HouseholdId, ResidentId) -> RpcOutcome<Unit>,
    private val leaveRpc: suspend (HouseholdId) -> RpcOutcome<Unit>,
    private val createInviteRpc: suspend (HouseholdId, HouseholdMemberRole) -> RpcOutcome<Invitation>,
    private val revokeInviteRpc: suspend (InvitationCode) -> RpcOutcome<Unit>,
    private val flow: AuthFlow,
    private val toast: ToastController,
    private val reauth: ReauthController,
) : ViewModel() {
    private data class LocalState(
        val issuedInvite: Invitation? = null,
        val submitting: Boolean = false,
    )

    private val local = MutableStateFlow(LocalState())

    val state: StateFlow<SettingsUiState> =
        combine(session.state, local) { s, l ->
            val activeId = s.activeHouseholdId
            val household = s.households?.list?.firstOrNull { it.id == activeId }
            SettingsUiState(
                displayName = s.displayName?.invoke() ?: "",
                households =
                    s.households?.list.orEmpty().map { h ->
                        HouseholdSummary(
                            id = h.id,
                            name = h.profile.name.invoke(),
                            memberCount = h.members.size(),
                            myRole =
                                s.residentId?.let { rid ->
                                    if (h.members.contains(rid)) h.members.roleOf(rid) else HouseholdMemberRole.閲覧者
                                } ?: HouseholdMemberRole.閲覧者,
                            active = h.id == activeId,
                        )
                    },
                activeId = activeId,
                activeName = household?.profile?.name?.invoke() ?: "",
                members =
                    household?.members?.list.orEmpty().map { m ->
                        MemberRow(
                            residentId = m.resident.id,
                            name =
                                m.resident.profile.displayName
                                    .invoke(),
                            role = m.role,
                            isMe = m.resident.id == s.residentId,
                        )
                    },
                isOwner = isOwner(s.households, activeId, s.residentId),
                issuedInvite = l.issuedInvite,
                submitting = l.submitting,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    private fun activeId(): HouseholdId? = state.value.activeId

    suspend fun renameDisplayName(name: DisplayName) {
        when (val r = renameDisplayNameRpc(name)) {
            is RpcOutcome.Success -> flow.applyDisplayName(name)
            is RpcOutcome.Failure -> failWith(r.error, null)
        }
    }

    suspend fun renameHousehold(name: HouseholdName) {
        val id = activeId() ?: return
        runRefreshing(renameHouseholdRpc(id, name), null)
    }

    suspend fun changeRole(
        target: ResidentId,
        role: HouseholdMemberRole,
    ) {
        val id = activeId() ?: return
        runRefreshing(changeRoleRpc(id, target, role), Res.string.settings_error_last_owner_change_role)
    }

    suspend fun removeMember(target: ResidentId) {
        val id = activeId() ?: return
        runRefreshing(removeMemberRpc(id, target), Res.string.settings_error_last_owner_remove)
    }

    suspend fun leave() {
        val id = activeId() ?: return
        when (val r = leaveRpc(id)) {
            is RpcOutcome.Success -> safe { flow.leaveActiveHousehold() }
            is RpcOutcome.Failure -> failWith(r.error, Res.string.settings_error_last_owner_leave)
        }
    }

    suspend fun createInvite(role: HouseholdMemberRole) {
        val id = activeId() ?: return
        when (val r = createInviteRpc(id, role)) {
            is RpcOutcome.Success -> local.value = local.value.copy(issuedInvite = r.value)
            is RpcOutcome.Failure -> failWith(r.error, null)
        }
    }

    suspend fun revokeInvite() {
        val invite = local.value.issuedInvite ?: return
        when (val r = revokeInviteRpc(invite.code)) {
            is RpcOutcome.Success -> local.value = local.value.copy(issuedInvite = null)
            is RpcOutcome.Failure -> failWith(r.error, null)
        }
    }

    fun switchHousehold(id: HouseholdId) {
        flow.switchActiveHousehold(id)
    }

    private suspend fun runRefreshing(
        outcome: RpcOutcome<Unit>,
        lastOwner: StringResource?,
    ) {
        when (outcome) {
            is RpcOutcome.Success -> safe { flow.refreshHouseholds() }
            is RpcOutcome.Failure -> failWith(outcome.error, lastOwner)
        }
    }

    /** Conflict かつ lastOwner 文言が指定されていれば専用文言、それ以外は errorText / reauth。 */
    private fun failWith(
        error: RpcError,
        lastOwner: StringResource?,
    ) {
        if (error.requiresReauth()) {
            reauth.request()
            return
        }
        if (error is RpcError.Conflict && lastOwner != null) {
            toast.show(UiText(lastOwner))
            return
        }
        toast.show(errorText(error))
    }

    /** AuthFlow 呼び出しの通信失敗を toast に倒す(Cancellation は再 throw)。 */
    private suspend fun safe(block: suspend () -> Unit) {
        try {
            block()
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            toast.show(errorText(RpcError.Internal("settings operation failed")))
        }
    }
}
