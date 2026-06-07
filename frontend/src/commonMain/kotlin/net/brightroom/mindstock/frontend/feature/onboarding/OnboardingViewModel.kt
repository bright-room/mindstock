package net.brightroom.mindstock.frontend.feature.onboarding

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.frontend.app.AuthFlow
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.errorText
import net.brightroom.mindstock.frontend.core.rpc.requiresReauth
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.rpc.result.RpcError

class OnboardingViewModel(
    private val registerDisplayName: suspend (DisplayName) -> RpcOutcome<Resident>,
    private val createHousehold: suspend (HouseholdName) -> RpcOutcome<Household>,
    private val flow: AuthFlow,
    private val toast: ToastController,
    private val reauth: ReauthController,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun setName(value: String) = _state.update { it.copy(name = value) }

    fun setHouseholdName(value: String) = _state.update { it.copy(householdName = value) }

    fun next() =
        _state.update {
            it.copy(
                step =
                    when (it.step) {
                        OnboardingStep.Welcome -> OnboardingStep.Name
                        OnboardingStep.Name -> OnboardingStep.Household
                        OnboardingStep.Household -> OnboardingStep.Confirm
                        OnboardingStep.Confirm -> OnboardingStep.Confirm
                    },
            )
        }

    fun back() =
        _state.update {
            it.copy(
                step =
                    when (it.step) {
                        OnboardingStep.Welcome -> OnboardingStep.Welcome
                        OnboardingStep.Name -> OnboardingStep.Welcome
                        OnboardingStep.Household -> OnboardingStep.Name
                        OnboardingStep.Confirm -> OnboardingStep.Household
                    },
            )
        }

    /** 確認 step の確定: 登録 → (世帯あり: 作成→enterApp / なし: needHousehold)。 */
    suspend fun submit() {
        val current = _state.value
        val displayName = runCatching { DisplayName(current.name) }.getOrNull()
        if (displayName == null) {
            toast.show(errorText(RpcError.BadRequest("displayName", "invalid")))
            return
        }
        _state.update { it.copy(submitting = true) }

        when (val reg = registerDisplayName(displayName)) {
            is RpcOutcome.Success -> {
                try {
                    flow.onResidentRegistered(reg.value)
                } catch (c: CancellationException) {
                    throw c
                } catch (_: Exception) {
                    toast.show(errorText(RpcError.Internal("reconnect failed")))
                    _state.update { it.copy(submitting = false) }
                    return
                }
                val rawHousehold = current.householdName.trim()
                if (rawHousehold.isEmpty()) {
                    flow.needHousehold()
                    return
                }
                val householdName = runCatching { HouseholdName(rawHousehold) }.getOrNull()
                if (householdName == null) {
                    toast.show(errorText(RpcError.BadRequest("householdName", "invalid")))
                    _state.update { it.copy(submitting = false) }
                    return
                }
                when (val created = createHousehold(householdName)) {
                    is RpcOutcome.Success -> {
                        enterOrEscape(created.value)
                    }

                    is RpcOutcome.Failure -> {
                        if (created.error.requiresReauth()) {
                            reauth.request()
                        } else {
                            toast.show(errorText(created.error))
                            flow.needHousehold()
                        }
                    }
                }
            }

            is RpcOutcome.Failure -> {
                if (reg.error.requiresReauth()) reauth.request() else toast.show(errorText(reg.error))
                _state.update { it.copy(submitting = false) }
            }
        }
    }

    private suspend fun enterOrEscape(household: Household) {
        try {
            flow.enterApp(household.id)
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
            toast.show(errorText(RpcError.Internal("enter failed")))
            flow.needHousehold()
        }
    }
}
