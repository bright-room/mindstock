package net.brightroom.mindstock.frontend.feature.shopping

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.toast_added_to_list
import mindstock.frontend.generated.resources.toast_removed_from_list
import mindstock.frontend.generated.resources.toast_replenished
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingList
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.errorText
import net.brightroom.mindstock.frontend.core.ui.FailureHandler
import net.brightroom.mindstock.frontend.core.ui.InventoryRefreshController
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.frontend.core.ui.UiText

class ShoppingListViewModel(
    private val householdId: HouseholdId,
    private val loadShoppingList: suspend (HouseholdId) -> RpcOutcome<ShoppingList>,
    private val setWantedFlag: suspend (ProductId, Boolean) -> RpcOutcome<Unit>,
    private val replenishStock: suspend (ProductId, Quantity, Note) -> RpcOutcome<Unit>,
    private val refresh: InventoryRefreshController,
    private val toast: ToastController,
    private val reauth: ReauthController,
) : ViewModel() {
    private val failure = FailureHandler(reauth, toast)

    private val _state = MutableStateFlow<ShoppingListUiState>(ShoppingListUiState.Loading)
    val state: StateFlow<ShoppingListUiState> = _state.asStateFlow()

    suspend fun load() {
        _state.value = ShoppingListUiState.Loading
        _state.value =
            when (val out = loadShoppingList(householdId)) {
                is RpcOutcome.Success -> {
                    ShoppingListUiState.Content(out.value)
                }

                is RpcOutcome.Failure -> {
                    failure.onLoadFailure(out.error)
                    ShoppingListUiState.Error(errorText(out.error))
                }
            }
    }

    suspend fun setWanted(
        productId: ProductId,
        wanted: Boolean,
    ) {
        val text = if (wanted) UiText(Res.string.toast_added_to_list) else UiText(Res.string.toast_removed_from_list)
        write(setWantedFlag(productId, wanted), text)
    }

    suspend fun replenish(
        productId: ProductId,
        quantity: Quantity,
        note: Note,
    ) = write(replenishStock(productId, quantity, note), UiText(Res.string.toast_replenished))

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
                failure.onMutationFailure(outcome.error)
            }
        }
    }
}
