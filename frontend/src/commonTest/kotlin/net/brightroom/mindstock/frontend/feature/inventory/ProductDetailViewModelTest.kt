package net.brightroom.mindstock.frontend.feature.inventory

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.ProductStatus
import net.brightroom.mindstock.domain.model.inventory.product.image.ProductImage
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.product.setting.StockingPolicy
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingEntry
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingList
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementIdentity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Reason
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.ui.InventoryRefreshController
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.rpc.result.RpcError
import kotlin.test.Test

private val actor = Resident(ResidentId.create(), Profile(DisplayName("テスト")))

private fun stockOf(
    id: ProductId,
    net: Int,
    min: Int = 1,
): Stock {
    val product =
        Product(
            id = id,
            name = ProductName("牛乳"),
            barcode = Barcode.Unlinked,
            setting = StockingPolicy(ProductUnit("本"), MinimumStock(min)),
            image = ProductImage.None,
            status = ProductStatus.採用中,
        )
    val movements =
        StockMovements(
            listOf(
                StockMovement.Replenishment(
                    identity = MovementIdentity.Pending,
                    quantity = Quantity(net),
                    occurredAt = OccurredAt.now(),
                    actor = actor,
                    note = Note(""),
                ),
            ),
        )
    return Stock(product, movements)
}

private fun vm(
    productId: ProductId,
    seed: Stock? = null,
    loadShoppingList: suspend (HouseholdId) -> RpcOutcome<ShoppingList> = { RpcOutcome.Success(ShoppingList(emptyList())) },
    loadHistory: suspend (ProductId) -> RpcOutcome<StockMovements> = { RpcOutcome.Success(StockMovements(emptyList())) },
    replenish: suspend (ProductId, Quantity, Note) -> RpcOutcome<Unit> = { _, _, _ -> RpcOutcome.Success(Unit) },
    consume: suspend (ProductId, Quantity, Note) -> RpcOutcome<Unit> = { _, _, _ -> RpcOutcome.Success(Unit) },
    correct: suspend (
        net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId,
        Quantity,
        Reason,
    ) -> RpcOutcome<Unit> = { _, _, _ -> RpcOutcome.Success(Unit) },
    setWanted: suspend (ProductId, Boolean) -> RpcOutcome<Unit> = { _, _ -> RpcOutcome.Success(Unit) },
    refresh: InventoryRefreshController = InventoryRefreshController(),
    toast: ToastController = ToastController(),
    reauth: ReauthController = ReauthController(),
) = ProductDetailViewModel(
    householdId = HouseholdId.create(),
    productId = productId,
    seed = seed,
    loadShoppingList = loadShoppingList,
    loadHistory = loadHistory,
    replenishStock = replenish,
    consumeStock = consume,
    correctMovement = correct,
    setWantedFlag = setWanted,
    refresh = refresh,
    toast = toast,
    reauth = reauth,
)

class ProductDetailViewModelTest {
    @Test
    fun load_resolves_stock_and_wanted_from_shopping_list() =
        runTest {
            val pid = ProductId.create()
            val entry = ShoppingEntry(stock = stockOf(pid, net = 5, min = 1), manuallyWanted = true)
            val v = vm(productId = pid, loadShoppingList = { RpcOutcome.Success(ShoppingList(listOf(entry))) })
            v.load()
            val content = v.state.value.shouldBeInstanceOf<ProductDetailUiState.Content>()
            content.wanted shouldBe true
            content.stock.product.id shouldBe pid
        }

    @Test
    fun load_uses_seed_when_entry_absent() =
        runTest {
            val pid = ProductId.create()
            val seed = stockOf(pid, net = 2)
            val v = vm(productId = pid, seed = seed, loadShoppingList = { RpcOutcome.Success(ShoppingList(emptyList())) })
            v.load()
            val content = v.state.value.shouldBeInstanceOf<ProductDetailUiState.Content>()
            content.wanted shouldBe false
            content.stock.product.id shouldBe pid
        }

    @Test
    fun load_errors_when_no_entry_and_no_seed() =
        runTest {
            val pid = ProductId.create()
            val v = vm(productId = pid, loadShoppingList = { RpcOutcome.Success(ShoppingList(emptyList())) })
            v.load()
            v.state.value.shouldBeInstanceOf<ProductDetailUiState.Error>()
        }

    @Test
    fun set_wanted_success_reloads_and_emits_refresh() =
        runTest {
            val pid = ProductId.create()
            val entry = ShoppingEntry(stock = stockOf(pid, net = 5), manuallyWanted = false)
            var loads = 0
            var refreshed = 0
            val refresh = InventoryRefreshController()
            val job = launch { refresh.signal.collect { refreshed++ } }
            runCurrent()
            val v =
                vm(
                    productId = pid,
                    loadShoppingList = {
                        loads++
                        RpcOutcome.Success(ShoppingList(listOf(entry)))
                    },
                    refresh = refresh,
                )
            v.load()
            v.setWanted(pid, true)
            runCurrent()
            loads shouldBe 2
            refreshed shouldBe 1
            job.cancel()
        }

    @Test
    fun unauthorized_on_load_requests_reauth() =
        runTest {
            var reauthRequested = 0
            val reauth = ReauthController()
            val job = launch { reauth.signal.collect { reauthRequested++ } }
            runCurrent()
            val v =
                vm(
                    productId = ProductId.create(),
                    loadShoppingList = { RpcOutcome.Failure(RpcError.Unauthorized("expired")) },
                    reauth = reauth,
                )
            v.load()
            runCurrent()
            reauthRequested shouldBe 1
            job.cancel()
        }
}
