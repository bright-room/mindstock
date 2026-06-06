package net.brightroom.mindstock.frontend.feature.activity

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.errorText
import net.brightroom.mindstock.frontend.core.rpc.requiresReauth
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.stock.ActivityFeed

class ActivityViewModel(
    private val householdId: HouseholdId,
    private val loadActivity: suspend (HouseholdId) -> RpcOutcome<ActivityFeed>,
    private val toast: ToastController,
    private val reauth: ReauthController,
) : ViewModel() {
    private val _state = MutableStateFlow<ActivityUiState>(ActivityUiState.Loading)
    val state: StateFlow<ActivityUiState> = _state.asStateFlow()

    suspend fun load() {
        _state.value = ActivityUiState.Loading
        _state.value =
            when (val out = loadActivity(householdId)) {
                is RpcOutcome.Success -> {
                    ActivityUiState.Content(out.value)
                }

                is RpcOutcome.Failure -> {
                    if (out.error.requiresReauth()) reauth.request() else toast.show(errorText(out.error))
                    ActivityUiState.Error(errorText(out.error))
                }
            }
    }
}
