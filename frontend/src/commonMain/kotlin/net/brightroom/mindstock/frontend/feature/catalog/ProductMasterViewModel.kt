package net.brightroom.mindstock.frontend.feature.catalog

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.toast_archived
import mindstock.frontend.generated.resources.toast_settings_saved
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.errorText
import net.brightroom.mindstock.frontend.core.rpc.requiresReauth
import net.brightroom.mindstock.frontend.core.ui.InventoryRefreshController
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.frontend.core.ui.UiText
import net.brightroom.mindstock.rpc.result.RpcError

class ProductMasterViewModel(
    private val householdId: HouseholdId,
    private val loadStocks: suspend (HouseholdId) -> RpcOutcome<Stocks>,
    private val changeUnitOf: suspend (ProductId, ProductUnit) -> RpcOutcome<Unit>,
    private val changeMinimumOf: suspend (ProductId, MinimumStock) -> RpcOutcome<Unit>,
    private val archiveProduct: suspend (ProductId) -> RpcOutcome<Unit>,
    private val refresh: InventoryRefreshController,
    private val toast: ToastController,
    private val reauth: ReauthController,
) : ViewModel() {
    private val _state = MutableStateFlow<ProductMasterUiState>(ProductMasterUiState.Loading)
    val state: StateFlow<ProductMasterUiState> = _state.asStateFlow()

    suspend fun load() {
        _state.value = ProductMasterUiState.Loading
        _state.value =
            when (val out = loadStocks(householdId)) {
                is RpcOutcome.Success -> {
                    ProductMasterUiState.Content(out.value)
                }

                is RpcOutcome.Failure -> {
                    handleFailure(out.error)
                    ProductMasterUiState.Error(errorText(out.error))
                }
            }
    }

    suspend fun changeUnit(
        productId: ProductId,
        unit: ProductUnit,
    ) = write(changeUnitOf(productId, unit), UiText(Res.string.toast_settings_saved))

    suspend fun changeMinimum(
        productId: ProductId,
        minimumStock: MinimumStock,
    ) = write(changeMinimumOf(productId, minimumStock), UiText(Res.string.toast_settings_saved))

    suspend fun archive(productId: ProductId) = write(archiveProduct(productId), UiText(Res.string.toast_archived))

    private suspend fun write(
        outcome: RpcOutcome<Unit>,
        successText: UiText,
    ) {
        when (outcome) {
            is RpcOutcome.Success -> {
                load()
                refresh.request()
                toast.show(successText)
            }

            is RpcOutcome.Failure -> {
                handleFailure(outcome.error)
            }
        }
    }

    private fun handleFailure(error: RpcError) {
        if (error.requiresReauth()) reauth.request() else toast.show(errorText(error))
    }
}
