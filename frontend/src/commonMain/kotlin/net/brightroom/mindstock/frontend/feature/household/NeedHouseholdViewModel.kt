package net.brightroom.mindstock.frontend.feature.household

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.join_code_invalid
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.frontend.app.AuthFlow
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.errorText
import net.brightroom.mindstock.frontend.core.rpc.requiresReauth
import net.brightroom.mindstock.frontend.core.ui.FailureHandler
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.frontend.core.ui.UiText
import net.brightroom.mindstock.rpc.household.InvitationPreview
import net.brightroom.mindstock.rpc.result.RpcError

class NeedHouseholdViewModel(
    private val createHousehold: suspend (HouseholdName) -> RpcOutcome<Household>,
    private val previewInvite: suspend (InvitationCode) -> RpcOutcome<InvitationPreview>,
    private val joinByCode: suspend (InvitationCode) -> RpcOutcome<Household>,
    private val flow: AuthFlow,
    private val toast: ToastController,
    private val reauth: ReauthController,
) : ViewModel() {
    private val failure = FailureHandler(reauth, toast)

    private val _state = MutableStateFlow(NeedHouseholdUiState())
    val state: StateFlow<NeedHouseholdUiState> = _state.asStateFlow()

    fun clearPreview() = _state.update { it.copy(preview = null, previewError = null) }

    suspend fun create(rawName: String) {
        val name =
            try {
                HouseholdName(rawName.trim())
            } catch (_: IllegalArgumentException) {
                toast.show(errorText(RpcError.BadRequest("householdName", "invalid")))
                return
            }
        _state.update { it.copy(busy = true) }
        when (val out = createHousehold(name)) {
            is RpcOutcome.Success -> {
                enterOrEscape(out.value)
            }

            is RpcOutcome.Failure -> {
                failure.onMutationFailure(out.error)
                _state.update { it.copy(busy = false) }
            }
        }
    }

    suspend fun preview(rawCode: String) {
        val code = parseCode(rawCode)
        if (code == null) {
            _state.update { it.copy(preview = null, previewError = invalidCodeText()) }
            return
        }
        when (val out = previewInvite(code)) {
            is RpcOutcome.Success -> {
                _state.update { it.copy(preview = out.value, previewError = null) }
            }

            is RpcOutcome.Failure -> {
                if (out.error.requiresReauth()) {
                    reauth.request()
                    _state.update { it.copy(preview = null, previewError = null) }
                } else {
                    _state.update { it.copy(preview = null, previewError = errorText(out.error)) }
                }
            }
        }
    }

    suspend fun join(rawCode: String) {
        val code = parseCode(rawCode)
        if (code == null) {
            _state.update { it.copy(previewError = invalidCodeText()) }
            return
        }
        _state.update { it.copy(busy = true) }
        when (val out = joinByCode(code)) {
            is RpcOutcome.Success -> {
                enterOrEscape(out.value)
            }

            is RpcOutcome.Failure -> {
                failure.onMutationFailure(out.error)
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun parseCode(raw: String): InvitationCode? = runCatching { InvitationCode(raw.trim().uppercase()) }.getOrNull()

    private fun invalidCodeText(): UiText = UiText(Res.string.join_code_invalid)

    private suspend fun enterOrEscape(household: Household) {
        try {
            flow.enterApp(household.id)
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
            toast.show(errorText(RpcError.Internal("enter failed")))
            _state.update { it.copy(busy = false) }
        }
    }
}
