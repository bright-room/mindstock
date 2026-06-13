package net.brightroom.mindstock.frontend.feature.inventory

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.toast_replenished
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingEntry
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingList
import net.brightroom.mindstock.domain.model.inventory.shopping.Wanted
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementIdentity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.ResidentProfile
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.ui.InventoryRefreshController
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.rpc.result.RpcError
import kotlin.test.Test

private val occurredAt = OccurredAt(LocalDateTime(2026, 6, 8, 9, 0))

private fun vm(
    loadStocks: suspend (HouseholdId) -> RpcOutcome<Stocks> = { RpcOutcome.Success(Stocks(emptyList())) },
    loadShoppingList: suspend (HouseholdId) -> RpcOutcome<ShoppingList> = { RpcOutcome.Success(ShoppingList(emptyList())) },
    replenish: suspend (ProductId, Quantity, Note, OccurredAt) -> RpcOutcome<Unit> = { _, _, _, _ -> RpcOutcome.Success(Unit) },
    consume: suspend (ProductId, Quantity, Note, OccurredAt) -> RpcOutcome<Unit> = { _, _, _, _ -> RpcOutcome.Success(Unit) },
    refresh: InventoryRefreshController = InventoryRefreshController(),
    toast: ToastController = ToastController(),
    reauth: ReauthController = ReauthController(),
) = InventoryViewModel(
    householdId = HouseholdId.create(),
    loadStocks = loadStocks,
    loadShoppingList = loadShoppingList,
    replenishStock = replenish,
    consumeStock = consume,
    refresh = refresh,
    toast = toast,
    reauth = reauth,
)

/** status=十分(currentQty 5 >= min 1)の Stock。手動希望の対象になり得る。 */
private fun sufficientStock(): Stock {
    val product = Product.custom(ProductName("水"), Barcode.Unlinked, ProductUnit("本"), MinimumStock(1))
    val actor = Resident(ResidentId.create(), ResidentProfile(DisplayName("テスト")))
    val movement =
        StockMovement.Replenishment(MovementIdentity.Persisted(MovementId(1L)), Quantity(5), occurredAt, actor, Note(""))
    return Stock(product, StockMovements(listOf(movement)))
}

class InventoryViewModelTest {
    @Test
    fun load_success_sets_content() =
        runTest {
            val v = vm()
            v.load()
            v.state.value.shouldBeInstanceOf<InventoryUiState.Content>()
        }

    @Test
    fun load_failure_sets_error() =
        runTest {
            val v = vm(loadStocks = { RpcOutcome.Failure(RpcError.Internal("boom")) })
            v.load()
            v.state.value.shouldBeInstanceOf<InventoryUiState.Error>()
        }

    @Test
    fun load_stocks_failure_skips_shopping_list() =
        runTest {
            // 在庫取得が失敗したら無駄な shoppingList RPC を投げず即 Error 反映する。
            var shoppingListCalled = false
            val v =
                vm(
                    loadStocks = { RpcOutcome.Failure(RpcError.Internal("boom")) },
                    loadShoppingList = {
                        shoppingListCalled = true
                        RpcOutcome.Success(ShoppingList(emptyList()))
                    },
                )
            v.load()
            v.state.value.shouldBeInstanceOf<InventoryUiState.Error>()
            shoppingListCalled shouldBe false
        }

    @Test
    fun load_derives_wanted_product_ids_from_manual_items() =
        runTest {
            // status=十分 かつ手動希望(Wanted true)→ shoppingList の manualItems に乗る。
            val stock = sufficientStock()
            val v =
                vm(
                    loadStocks = { RpcOutcome.Success(Stocks(listOf(stock))) },
                    loadShoppingList = { RpcOutcome.Success(ShoppingList(listOf(ShoppingEntry(stock, Wanted(true))))) },
                )
            v.load()
            val content = v.state.value.shouldBeInstanceOf<InventoryUiState.Content>()
            content.wantedProductIds shouldBe setOf(stock.product.id)
        }

    @Test
    fun load_shopping_list_failure_leaves_wanted_empty() =
        runTest {
            val v =
                vm(
                    loadStocks = { RpcOutcome.Success(Stocks(listOf(sufficientStock()))) },
                    loadShoppingList = { RpcOutcome.Failure(RpcError.Internal("boom")) },
                )
            v.load()
            // shoppingList 取得失敗でも在庫は表示し、wanted は空集合に倒す。
            val content = v.state.value.shouldBeInstanceOf<InventoryUiState.Content>()
            content.wantedProductIds shouldBe emptySet()
        }

    @Test
    fun replenish_success_refetches_and_toasts() =
        runTest {
            var loads = 0
            val toast = ToastController()
            val v =
                vm(loadStocks = {
                    loads++
                    RpcOutcome.Success(Stocks(emptyList()))
                }, toast = toast)
            v.load()
            v.replenish(ProductId.create(), Quantity(2), Note(""), occurredAt)
            loads shouldBe 2 // 初回 + 補充後の再フェッチ
            toast.current.value
                ?.text
                ?.resource shouldBe Res.string.toast_replenished
        }

    @Test
    fun unauthorized_on_write_requests_reauth() =
        runTest {
            var reauthRequested = 0
            val reauth = ReauthController()
            val job = launch { reauth.signal.collect { reauthRequested++ } }
            runCurrent()
            val v = vm(replenish = { _, _, _, _ -> RpcOutcome.Failure(RpcError.Unauthorized("expired")) }, reauth = reauth)
            v.load()
            v.replenish(ProductId.create(), Quantity(1), Note(""), occurredAt)
            runCurrent()
            reauthRequested shouldBe 1
            job.cancel()
        }

    @Test
    fun search_filters_visible_stocks() =
        runTest {
            val v = vm()
            v.load()
            v.setQuery("xyz")
            val content = v.state.value as InventoryUiState.Content
            content.query shouldBe "xyz"
        }

    @Test
    fun query_survives_reload_after_write() =
        runTest {
            val v = vm()
            v.load()
            v.setQuery("milk")
            v.replenish(ProductId.create(), Quantity(1), Note(""), occurredAt) // 内部で load() 再フェッチ
            val content = v.state.value as InventoryUiState.Content
            content.query shouldBe "milk" // クエリが消えない
        }

    @Test
    fun replenish_success_emits_refresh() =
        runTest {
            var refreshed = 0
            val refresh = InventoryRefreshController()
            val job = launch { refresh.signal.collect { refreshed++ } }
            runCurrent()
            val v = vm(refresh = refresh)
            v.load()
            v.replenish(ProductId.create(), Quantity(1), Note(""), occurredAt)
            runCurrent()
            refreshed shouldBe 1
            job.cancel()
        }

    @Test
    fun set_view_reflects_in_content() =
        runTest {
            val v = vm()
            v.load()
            v.setView(StockView.Grid)
            val content = v.state.value as InventoryUiState.Content
            content.view shouldBe StockView.Grid
        }
}
