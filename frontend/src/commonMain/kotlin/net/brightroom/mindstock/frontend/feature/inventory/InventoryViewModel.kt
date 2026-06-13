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
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingList
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.errorText
import net.brightroom.mindstock.frontend.core.ui.FailureHandler
import net.brightroom.mindstock.frontend.core.ui.InventoryRefreshController
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.frontend.core.ui.UiText

class InventoryViewModel(
    private val householdId: HouseholdId,
    private val loadStocks: suspend (HouseholdId) -> RpcOutcome<Stocks>,
    private val loadShoppingList: suspend (HouseholdId) -> RpcOutcome<ShoppingList>,
    private val replenishStock: suspend (ProductId, Quantity, Note, OccurredAt) -> RpcOutcome<Unit>,
    private val consumeStock: suspend (ProductId, Quantity, Note, OccurredAt) -> RpcOutcome<Unit>,
    private val refresh: InventoryRefreshController,
    private val toast: ToastController,
    private val reauth: ReauthController,
) : ViewModel() {
    private val failure = FailureHandler(reauth, toast)

    // 最後の取得結果。null = 未ロード(= Loading)。collect されず recomputeState() で同期読みするだけの
    // imperative な可変ホルダーなので reactive を示唆する StateFlow ではなく素の var にする。
    // private sentinel なので nullable を許容
    private var lastLoadResult: RpcOutcome<Stocks>? = null

    // status=十分 かつ手動希望の商品 ID(shoppingList の manualItems 由来)。
    // 在庫表示を止めないため、shoppingList 取得失敗時は空集合(バッジ非表示)に倒す。
    private var lastWantedIds: Set<ProductId> = emptySet()

    // view / query は load() の再フェッチ（補充消費後）でも保持するため独立した source of truth に持つ。
    private val _view = MutableStateFlow(StockView.List)
    val view: StateFlow<StockView> = _view.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow<InventoryUiState>(InventoryUiState.Loading)
    val state: StateFlow<InventoryUiState> = _state.asStateFlow()

    /** lastLoadResult / _view / _query の 3 source から状態を合成して _state へ即時反映する。 */
    private fun recomputeState() {
        _state.value =
            when (val result = lastLoadResult) {
                null -> InventoryUiState.Loading
                is RpcOutcome.Success -> InventoryUiState.Content(result.value, _view.value, _query.value, lastWantedIds)
                is RpcOutcome.Failure -> InventoryUiState.Error(errorText(result.error))
            }
    }

    suspend fun load() {
        val out = loadStocks(householdId)
        if (out is RpcOutcome.Failure) failure.onLoadFailure(out.error)
        lastLoadResult = out
        // 手動希望の overlay(バッジ / need 件数)。在庫表示を止めないため失敗時は空集合に倒す。
        val wantedOut = loadShoppingList(householdId)
        lastWantedIds =
            (wantedOut as? RpcOutcome.Success)
                ?.value
                ?.manualItems()
                ?.list
                ?.map { it.stock.product.id }
                ?.toSet()
                ?: emptySet()
        recomputeState()
    }

    fun setView(v: StockView) {
        _view.value = v
        recomputeState()
    }

    fun setQuery(query: String) {
        _query.value = query
        recomputeState()
    }

    suspend fun replenish(
        productId: ProductId,
        quantity: Quantity,
        note: Note,
        occurredAt: OccurredAt,
    ) = write(replenishStock(productId, quantity, note, occurredAt), UiText(Res.string.toast_replenished))

    suspend fun consume(
        productId: ProductId,
        quantity: Quantity,
        note: Note,
        occurredAt: OccurredAt,
    ) = write(consumeStock(productId, quantity, note, occurredAt), UiText(Res.string.toast_consumed))

    private suspend fun write(
        outcome: RpcOutcome<Unit>,
        successText: UiText,
    ) {
        when (outcome) {
            is RpcOutcome.Success -> {
                load() // append-only のサーバ真実を再取得
                refresh.request() // 他タブ（買い物/活動）へ波及
                toast.show(successText)
            }

            is RpcOutcome.Failure -> {
                failure.onMutationFailure(outcome.error)
            }
        }
    }
}
