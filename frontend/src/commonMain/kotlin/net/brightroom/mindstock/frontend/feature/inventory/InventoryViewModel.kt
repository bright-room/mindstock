package net.brightroom.mindstock.frontend.feature.inventory

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.errorText

class InventoryViewModel(
    private val householdId: HouseholdId,
    private val loadStocks: suspend (HouseholdId) -> RpcOutcome<Stocks>,
) : ViewModel() {
    private val _state = MutableStateFlow<InventoryUiState>(InventoryUiState.Loading)
    val state: StateFlow<InventoryUiState> = _state.asStateFlow()

    private val _view = MutableStateFlow(StockView.List)
    val view: StateFlow<StockView> = _view.asStateFlow()

    suspend fun load() {
        _state.value = InventoryUiState.Loading
        _state.value =
            when (val out = loadStocks(householdId)) {
                is RpcOutcome.Success -> InventoryUiState.Content(out.value, _view.value)
                is RpcOutcome.Failure -> InventoryUiState.Error(errorText(out.error))
            }
    }

    fun setView(v: StockView) {
        _view.value = v
        val s = _state.value
        if (s is InventoryUiState.Content) _state.value = s.copy(view = v)
    }
}
