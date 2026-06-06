package net.brightroom.mindstock.frontend.feature.inventory

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.toast_corrected
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Reason
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.errorText
import net.brightroom.mindstock.frontend.core.rpc.requiresReauth
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.frontend.core.ui.UiText
import net.brightroom.mindstock.rpc.result.RpcError

class ProductDetailViewModel(
    private val productId: ProductId,
    private val loadHistory: suspend (ProductId) -> RpcOutcome<StockMovements>,
    private val correctMovement: suspend (MovementId, Quantity, Reason) -> RpcOutcome<Unit>,
    private val toast: ToastController,
    private val reauth: ReauthController,
) : ViewModel() {
    private val _state = MutableStateFlow<ProductDetailUiState>(ProductDetailUiState.Loading)
    val state: StateFlow<ProductDetailUiState> = _state.asStateFlow()

    suspend fun load() {
        _state.value = ProductDetailUiState.Loading
        _state.value =
            when (val out = loadHistory(productId)) {
                is RpcOutcome.Success -> ProductDetailUiState.Content(out.value)
                is RpcOutcome.Failure -> {
                    handleFailure(out.error)
                    ProductDetailUiState.Error(errorText(out.error))
                }
            }
    }

    suspend fun correct(target: MovementId, correctedQuantity: Quantity, reason: Reason) {
        when (val out = correctMovement(target, correctedQuantity, reason)) {
            is RpcOutcome.Success -> {
                load()
                toast.show(UiText(Res.string.toast_corrected))
            }
            is RpcOutcome.Failure -> handleFailure(out.error)
        }
    }

    private fun handleFailure(error: RpcError) {
        if (error.requiresReauth()) reauth.request() else toast.show(errorText(error))
    }
}
