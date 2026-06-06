package net.brightroom.mindstock.frontend.feature.catalog

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.toast_product_added
import mindstock.frontend.generated.resources.toast_product_adopted
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItems
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.errorText
import net.brightroom.mindstock.frontend.core.rpc.requiresReauth
import net.brightroom.mindstock.frontend.core.ui.InventoryRefreshController
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.frontend.core.ui.UiText
import net.brightroom.mindstock.rpc.product.AddCustomProductRequest
import net.brightroom.mindstock.rpc.result.RpcError

private const val SEARCH_LIMIT = 20

class AddProductViewModel(
    private val householdId: HouseholdId,
    private val searchCatalog: suspend (CatalogItemName, Int) -> RpcOutcome<CatalogItems>,
    private val lookupJan: suspend (Jan) -> RpcOutcome<CatalogItem>,
    private val adoptProduct: suspend (CatalogItemId, ProductUnit, MinimumStock) -> RpcOutcome<Product>,
    private val addCustomProduct: suspend (AddCustomProductRequest) -> RpcOutcome<Product>,
    private val refresh: InventoryRefreshController,
    private val toast: ToastController,
    private val reauth: ReauthController,
) : ViewModel() {
    private val _state = MutableStateFlow<AddProductUiState>(AddProductUiState.Browsing())
    val state: StateFlow<AddProductUiState> = _state.asStateFlow()

    suspend fun search(rawQuery: String) {
        val q = rawQuery.trim()
        if (q.isEmpty()) {
            _state.value = AddProductUiState.Browsing()
            return
        }
        _state.value = AddProductUiState.Browsing(phase = BrowsePhase.Searching)
        _state.value =
            when (val out = searchCatalog(CatalogItemName(q), SEARCH_LIMIT)) {
                is RpcOutcome.Success -> {
                    AddProductUiState.Browsing(results = out.value, phase = BrowsePhase.Idle)
                }

                is RpcOutcome.Failure -> {
                    handleFailure(out.error)
                    AddProductUiState.Browsing(phase = BrowsePhase.Idle)
                }
            }
    }

    suspend fun lookupByJan(jan: Jan) {
        _state.value = AddProductUiState.Browsing(phase = BrowsePhase.JanLookingUp)
        _state.value =
            when (val out = lookupJan(jan)) {
                is RpcOutcome.Success -> {
                    AddProductUiState.AdoptForm(out.value)
                }

                is RpcOutcome.Failure -> {
                    if (out.error is RpcError.NotFound) {
                        AddProductUiState.CustomForm(seedName = "", jan = jan, nameLocked = false)
                    } else {
                        handleFailure(out.error)
                        AddProductUiState.Browsing(phase = BrowsePhase.Idle)
                    }
                }
            }
    }

    fun pickCatalog(item: CatalogItem) {
        _state.value = AddProductUiState.AdoptForm(item)
    }

    fun pickCustom(seedName: String) {
        _state.value = AddProductUiState.CustomForm(seedName = seedName.trim(), jan = null, nameLocked = false)
    }

    fun backToBrowsing() {
        _state.value = AddProductUiState.Browsing()
    }

    suspend fun adopt(
        item: CatalogItem,
        unit: ProductUnit,
        minimumStock: MinimumStock,
    ) = submit(adoptProduct(item.id, unit, minimumStock), UiText(Res.string.toast_product_adopted))

    suspend fun addCustom(
        name: ProductName,
        jan: Jan?,
        unit: ProductUnit,
        minimumStock: MinimumStock,
    ) {
        val barcode = if (jan == null) Barcode.Unlinked else Barcode.Linked(jan)
        submit(
            addCustomProduct(AddCustomProductRequest(name = name, unit = unit, barcode = barcode, minimumStock = minimumStock)),
            UiText(Res.string.toast_product_added),
        )
    }

    private suspend fun submit(
        outcome: RpcOutcome<Product>,
        successText: UiText,
    ) {
        when (outcome) {
            is RpcOutcome.Success -> {
                refresh.request()
                toast.show(successText)
                _state.value = AddProductUiState.Done
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
