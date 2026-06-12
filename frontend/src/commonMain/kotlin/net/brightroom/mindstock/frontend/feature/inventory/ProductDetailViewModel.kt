package net.brightroom.mindstock.frontend.feature.inventory

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.toast_added_to_list
import mindstock.frontend.generated.resources.toast_consumed
import mindstock.frontend.generated.resources.toast_corrected
import mindstock.frontend.generated.resources.toast_removed_from_list
import mindstock.frontend.generated.resources.toast_replenished
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingList
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Reason
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.errorText
import net.brightroom.mindstock.frontend.core.rpc.requiresReauth
import net.brightroom.mindstock.frontend.core.ui.InventoryRefreshController
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.frontend.core.ui.UiText
import net.brightroom.mindstock.rpc.result.RpcError

class ProductDetailViewModel(
    private val householdId: HouseholdId,
    private val productId: ProductId,
    private val seed: Stock?,
    private val loadShoppingList: suspend (HouseholdId) -> RpcOutcome<ShoppingList>,
    private val loadHistory: suspend (ProductId) -> RpcOutcome<StockMovements>,
    private val replenishStock: suspend (ProductId, Quantity, Note, OccurredAt) -> RpcOutcome<Unit>,
    private val consumeStock: suspend (ProductId, Quantity, Note, OccurredAt) -> RpcOutcome<Unit>,
    private val correctMovement: suspend (MovementId, Quantity, Reason) -> RpcOutcome<Unit>,
    private val setWantedFlag: suspend (ProductId, Boolean) -> RpcOutcome<Unit>,
    private val refresh: InventoryRefreshController,
    private val toast: ToastController,
    private val reauth: ReauthController,
) : ViewModel() {
    private val _state = MutableStateFlow<ProductDetailUiState>(ProductDetailUiState.Loading)
    val state: StateFlow<ProductDetailUiState> = _state.asStateFlow()

    suspend fun load() {
        _state.value = ProductDetailUiState.Loading

        // Stock + wanted を shoppingList から解決
        val resolved: Pair<Stock, Boolean>? =
            when (val out = loadShoppingList(householdId)) {
                is RpcOutcome.Success -> {
                    val entry = out.value.list.firstOrNull { it.stock.product.id == productId }
                    when {
                        entry != null -> entry.stock to entry.manuallyWanted()
                        seed != null -> seed to false
                        else -> null
                    }
                }

                is RpcOutcome.Failure -> {
                    handleFailure(out.error)
                    _state.value = ProductDetailUiState.Error(errorText(out.error))
                    return
                }
            }

        if (resolved == null) {
            _state.value = ProductDetailUiState.Error(errorText(RpcError.NotFound("product not found: $productId")))
            return
        }

        val (stock, wanted) = resolved
        val movements =
            when (val out = loadHistory(productId)) {
                is RpcOutcome.Success -> {
                    out.value
                }

                is RpcOutcome.Failure -> {
                    handleFailure(out.error)
                    _state.value = ProductDetailUiState.Error(errorText(out.error))
                    return
                }
            }
        _state.value = ProductDetailUiState.Content(stock, wanted, movements)
    }

    suspend fun replenish(
        quantity: Quantity,
        note: Note,
        occurredAt: OccurredAt,
    ) = write(replenishStock(productId, quantity, note, occurredAt), UiText(Res.string.toast_replenished))

    suspend fun consume(
        quantity: Quantity,
        note: Note,
        occurredAt: OccurredAt,
    ) = write(consumeStock(productId, quantity, note, occurredAt), UiText(Res.string.toast_consumed))

    suspend fun correct(
        target: MovementId,
        correctedQuantity: Quantity,
        reason: Reason,
    ) = write(correctMovement(target, correctedQuantity, reason), UiText(Res.string.toast_corrected))

    suspend fun setWanted(
        productId: ProductId,
        wanted: Boolean,
    ) {
        val text = if (wanted) UiText(Res.string.toast_added_to_list) else UiText(Res.string.toast_removed_from_list)
        write(setWantedFlag(productId, wanted), text)
    }

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
