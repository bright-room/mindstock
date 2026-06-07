package net.brightroom.mindstock.frontend.feature.catalog

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.toast_unarchived
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.Products
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.errorText
import net.brightroom.mindstock.frontend.core.rpc.requiresReauth
import net.brightroom.mindstock.frontend.core.ui.InventoryRefreshController
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.frontend.core.ui.UiText
import net.brightroom.mindstock.rpc.result.RpcError

class ArchivedViewModel(
    private val householdId: HouseholdId,
    private val loadArchived: suspend (HouseholdId) -> RpcOutcome<Products>,
    private val unarchiveProduct: suspend (ProductId) -> RpcOutcome<Unit>,
    private val refresh: InventoryRefreshController,
    private val toast: ToastController,
    private val reauth: ReauthController,
) : ViewModel() {
    private val _state = MutableStateFlow<ArchivedUiState>(ArchivedUiState.Loading)
    val state: StateFlow<ArchivedUiState> = _state.asStateFlow()

    suspend fun load() {
        _state.value = ArchivedUiState.Loading
        _state.value =
            when (val out = loadArchived(householdId)) {
                is RpcOutcome.Success -> {
                    ArchivedUiState.Content(out.value)
                }

                is RpcOutcome.Failure -> {
                    handleFailure(out.error)
                    ArchivedUiState.Error(errorText(out.error))
                }
            }
    }

    suspend fun unarchive(productId: ProductId) {
        when (val out = unarchiveProduct(productId)) {
            is RpcOutcome.Success -> {
                load()
                refresh.request()
                toast.show(UiText(Res.string.toast_unarchived))
            }

            is RpcOutcome.Failure -> {
                handleFailure(out.error)
            }
        }
    }

    private fun handleFailure(error: RpcError) {
        if (error.requiresReauth()) reauth.request() else toast.show(errorText(error))
    }
}
