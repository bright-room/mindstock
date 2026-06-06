package net.brightroom.mindstock.frontend.feature.shopping

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingList
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.ui.InventoryRefreshController
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.rpc.result.RpcError
import kotlin.test.Test

private fun vm(
    loadShoppingList: suspend (HouseholdId) -> RpcOutcome<ShoppingList> = { RpcOutcome.Success(ShoppingList(emptyList())) },
    setWanted: suspend (ProductId, Boolean) -> RpcOutcome<Unit> = { _, _ -> RpcOutcome.Success(Unit) },
    replenish: suspend (ProductId, Quantity, Note) -> RpcOutcome<Unit> = { _, _, _ -> RpcOutcome.Success(Unit) },
    refresh: InventoryRefreshController = InventoryRefreshController(),
    toast: ToastController = ToastController(),
    reauth: ReauthController = ReauthController(),
) = ShoppingListViewModel(
    householdId = HouseholdId.create(),
    loadShoppingList = loadShoppingList,
    setWantedFlag = setWanted,
    replenishStock = replenish,
    refresh = refresh,
    toast = toast,
    reauth = reauth,
)

class ShoppingListViewModelTest {
    @Test
    fun load_success_sets_content() =
        runTest {
            val v = vm()
            v.load()
            v.state.value.shouldBeInstanceOf<ShoppingListUiState.Content>()
        }

    @Test
    fun load_failure_sets_error() =
        runTest {
            val v = vm(loadShoppingList = { RpcOutcome.Failure(RpcError.Internal("boom")) })
            v.load()
            v.state.value.shouldBeInstanceOf<ShoppingListUiState.Error>()
        }

    @Test
    fun set_wanted_success_reloads_and_emits_refresh() =
        runTest {
            var loads = 0
            var refreshed = 0
            val refresh = InventoryRefreshController()
            val job = launch { refresh.signal.collect { refreshed++ } }
            runCurrent()
            val v =
                vm(loadShoppingList = {
                    loads++
                    RpcOutcome.Success(ShoppingList(emptyList()))
                }, refresh = refresh)
            v.load()
            v.setWanted(ProductId.create(), false)
            runCurrent()
            loads shouldBe 2
            refreshed shouldBe 1
            job.cancel()
        }

    @Test
    fun unauthorized_requests_reauth() =
        runTest {
            var reauthRequested = 0
            val reauth = ReauthController()
            val job = launch { reauth.signal.collect { reauthRequested++ } }
            runCurrent()
            val v = vm(setWanted = { _, _ -> RpcOutcome.Failure(RpcError.Unauthorized("expired")) }, reauth = reauth)
            v.setWanted(ProductId.create(), true)
            runCurrent()
            reauthRequested shouldBe 1
            job.cancel()
        }
}
