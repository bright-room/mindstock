package net.brightroom.mindstock.frontend.feature.catalog

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.ui.InventoryRefreshController
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.rpc.result.RpcError
import kotlin.test.Test

private fun vm(
    load: suspend (HouseholdId) -> RpcOutcome<Stocks> = { RpcOutcome.Success(Stocks(emptyList())) },
    changeUnit: suspend (ProductId, ProductUnit) -> RpcOutcome<Unit> = { _, _ -> RpcOutcome.Success(Unit) },
    changeMin: suspend (ProductId, MinimumStock) -> RpcOutcome<Unit> = { _, _ -> RpcOutcome.Success(Unit) },
    archive: suspend (ProductId) -> RpcOutcome<Unit> = { RpcOutcome.Success(Unit) },
    refresh: InventoryRefreshController = InventoryRefreshController(),
    toast: ToastController = ToastController(),
    reauth: ReauthController = ReauthController(),
) = ProductMasterViewModel(
    householdId = HouseholdId.create(),
    loadStocks = load,
    changeUnitOf = changeUnit,
    changeMinimumOf = changeMin,
    archiveProduct = archive,
    refresh = refresh,
    toast = toast,
    reauth = reauth,
)

class ProductMasterViewModelTest {
    @Test fun load_success_sets_content() =
        runTest {
            val v = vm()
            v.load()
            v.state.value.shouldBeInstanceOf<ProductMasterUiState.Content>()
        }

    @Test fun load_failure_sets_error() =
        runTest {
            val v = vm(load = { RpcOutcome.Failure(RpcError.Internal("boom")) })
            v.load()
            v.state.value.shouldBeInstanceOf<ProductMasterUiState.Error>()
        }

    @Test fun change_unit_success_reloads() =
        runTest {
            var loads = 0
            val v =
                vm(load = {
                    loads++
                    RpcOutcome.Success(Stocks(emptyList()))
                })
            v.load()
            v.changeUnit(ProductId.create(), ProductUnit("本"))
            loads shouldBe 2
        }

    @Test fun change_minimum_success_reloads() =
        runTest {
            var loads = 0
            val v =
                vm(load = {
                    loads++
                    RpcOutcome.Success(Stocks(emptyList()))
                })
            v.load()
            v.changeMinimum(ProductId.create(), MinimumStock(3))
            loads shouldBe 2
        }

    @Test fun archive_success_reloads() =
        runTest {
            var loads = 0
            val v =
                vm(load = {
                    loads++
                    RpcOutcome.Success(Stocks(emptyList()))
                })
            v.load()
            v.archive(ProductId.create())
            loads shouldBe 2
        }

    @Test fun archive_unauthorized_requests_reauth() =
        runTest {
            var n = 0
            val reauth = ReauthController()
            val job = launch { reauth.signal.collect { n++ } }
            runCurrent()
            val v = vm(archive = { RpcOutcome.Failure(RpcError.Unauthorized("x")) }, reauth = reauth)
            v.archive(ProductId.create())
            runCurrent()
            n shouldBe 1
            job.cancel()
        }
}
