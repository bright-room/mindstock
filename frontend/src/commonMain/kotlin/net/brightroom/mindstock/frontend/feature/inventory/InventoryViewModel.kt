package net.brightroom.mindstock.frontend.feature.inventory

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.toast_consumed
import mindstock.frontend.generated.resources.toast_replenished
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.errorText
import net.brightroom.mindstock.frontend.core.rpc.requiresReauth
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.frontend.core.ui.UiText

class InventoryViewModel(
    private val householdId: HouseholdId,
    private val loadStocks: suspend (HouseholdId) -> RpcOutcome<Stocks>,
    private val replenishStock: suspend (ProductId, Quantity, Note) -> RpcOutcome<Unit>,
    private val consumeStock: suspend (ProductId, Quantity, Note) -> RpcOutcome<Unit>,
    private val toast: ToastController,
    private val reauth: ReauthController,
) : ViewModel() {
    private val _state = MutableStateFlow<InventoryUiState>(InventoryUiState.Loading)
    val state: StateFlow<InventoryUiState> = _state.asStateFlow()

    // view / query は load() の再フェッチ（補充消費後）でも保持するため独立した source of truth に持つ。
    private val _view = MutableStateFlow(StockView.List)
    val view: StateFlow<StockView> = _view.asStateFlow()

    private val _query = MutableStateFlow("")

    suspend fun load() {
        _state.value = InventoryUiState.Loading
        _state.value =
            when (val out = loadStocks(householdId)) {
                is RpcOutcome.Success -> InventoryUiState.Content(out.value, _view.value, _query.value)
                is RpcOutcome.Failure -> {
                    handleFailure(out.error)
                    InventoryUiState.Error(errorText(out.error))
                }
            }
    }

    fun setView(v: StockView) {
        _view.value = v
        val s = _state.value
        if (s is InventoryUiState.Content) _state.value = s.copy(view = v)
    }

    fun setQuery(query: String) {
        _query.value = query
        val s = _state.value
        if (s is InventoryUiState.Content) _state.value = s.copy(query = query)
    }

    suspend fun replenish(productId: ProductId, quantity: Quantity, note: Note) =
        write(replenishStock(productId, quantity, note), UiText(Res.string.toast_replenished))

    suspend fun consume(productId: ProductId, quantity: Quantity, note: Note) =
        write(consumeStock(productId, quantity, note), UiText(Res.string.toast_consumed))

    private suspend fun write(outcome: RpcOutcome<Unit>, successText: UiText) {
        when (outcome) {
            is RpcOutcome.Success -> {
                load() // append-only のサーバ真実を再取得
                toast.show(successText)
            }
            is RpcOutcome.Failure -> handleFailure(outcome.error)
        }
    }

    private fun handleFailure(error: net.brightroom.mindstock.rpc.result.RpcError) {
        if (error.requiresReauth()) {
            reauth.request()
        } else {
            toast.show(errorText(error))
        }
    }
}
