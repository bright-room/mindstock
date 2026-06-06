package net.brightroom.mindstock.frontend.feature.catalog

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItems
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.ui.InventoryRefreshController
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.rpc.product.AddCustomProductRequest
import net.brightroom.mindstock.rpc.result.RpcError
import kotlin.test.Test

private val sampleJan = Jan("4901234567894")

private fun item() = CatalogItem(CatalogItemId.create(), sampleJan, CatalogItemName("麦茶"))

private fun adoptedProduct(): Product = Product.adopt(item(), ProductUnit("個"), MinimumStock(1))

private fun vm(
    search: suspend (CatalogItemName, Int) -> RpcOutcome<CatalogItems> = { _, _ -> RpcOutcome.Success(CatalogItems(emptyList())) },
    lookup: suspend (Jan) -> RpcOutcome<CatalogItem> = { RpcOutcome.Success(item()) },
    adopt: suspend (CatalogItemId, ProductUnit, MinimumStock) -> RpcOutcome<Product> = { _, _, _ -> RpcOutcome.Success(adoptedProduct()) },
    addCustom: suspend (AddCustomProductRequest) -> RpcOutcome<Product> = { RpcOutcome.Success(adoptedProduct()) },
    refresh: InventoryRefreshController = InventoryRefreshController(),
    toast: ToastController = ToastController(),
    reauth: ReauthController = ReauthController(),
) = AddProductViewModel(
    searchCatalog = search,
    lookupJan = lookup,
    adoptProduct = adopt,
    addCustomProduct = addCustom,
    refresh = refresh,
    toast = toast,
    reauth = reauth,
)

class AddProductViewModelTest {
    @Test
    fun search_blank_resets_to_idle() =
        runTest {
            val v = vm()
            v.search("   ")
            val s = v.state.value
            s.shouldBeInstanceOf<AddProductUiState.Browsing>()
            s.phase shouldBe BrowsePhase.Idle
        }

    @Test
    fun search_name_sets_results() =
        runTest {
            val v = vm(search = { _, _ -> RpcOutcome.Success(CatalogItems(listOf(item()))) })
            v.search("麦茶")
            val s = v.state.value
            s.shouldBeInstanceOf<AddProductUiState.Browsing>()
            s.results.size() shouldBe 1
        }

    @Test
    fun lookup_hit_moves_to_adopt_form() =
        runTest {
            val v = vm(lookup = { RpcOutcome.Success(item()) })
            v.lookupByJan(sampleJan)
            v.state.value.shouldBeInstanceOf<AddProductUiState.AdoptForm>()
        }

    @Test
    fun lookup_not_found_moves_to_custom_form() =
        runTest {
            val v = vm(lookup = { RpcOutcome.Failure(RpcError.NotFound("none")) })
            v.lookupByJan(sampleJan)
            val s = v.state.value
            s.shouldBeInstanceOf<AddProductUiState.CustomForm>()
            s.jan shouldBe sampleJan
            s.nameLocked shouldBe false
        }

    @Test
    fun adopt_success_sets_done() =
        runTest {
            val v = vm()
            v.adopt(item(), ProductUnit("個"), MinimumStock(1))
            v.state.value shouldBe AddProductUiState.Done
        }

    @Test
    fun add_custom_success_sets_done() =
        runTest {
            val v = vm()
            v.addCustom(
                net.brightroom.mindstock.domain.model.inventory.product
                    .ProductName("麦茶パック"),
                null,
                ProductUnit("袋"),
                MinimumStock(2),
            )
            v.state.value shouldBe AddProductUiState.Done
        }

    @Test
    fun adopt_unauthorized_requests_reauth() =
        runTest {
            var n = 0
            val reauth = ReauthController()
            val job = launch { reauth.signal.collect { n++ } }
            runCurrent()
            val v = vm(adopt = { _, _, _ -> RpcOutcome.Failure(RpcError.Unauthorized("x")) }, reauth = reauth)
            v.adopt(item(), ProductUnit("個"), MinimumStock(1))
            runCurrent()
            n shouldBe 1
            job.cancel()
        }

    @Test
    fun pick_custom_sets_editable_custom_form() =
        runTest {
            val v = vm()
            v.pickCustom("自作茶")
            val s = v.state.value
            s.shouldBeInstanceOf<AddProductUiState.CustomForm>()
            s.nameLocked shouldBe false
            s.jan shouldBe null
        }
}
