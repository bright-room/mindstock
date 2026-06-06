package net.brightroom.mindstock.frontend.feature.catalog

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.Products
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.ui.InventoryRefreshController
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.rpc.result.RpcError
import kotlin.test.Test

private fun vm(
    load: suspend (HouseholdId) -> RpcOutcome<Products> = { RpcOutcome.Success(Products(emptyList())) },
    unarchive: suspend (ProductId) -> RpcOutcome<Unit> = { RpcOutcome.Success(Unit) },
    refresh: InventoryRefreshController = InventoryRefreshController(),
    toast: ToastController = ToastController(),
    reauth: ReauthController = ReauthController(),
) = ArchivedViewModel(
    householdId = HouseholdId.create(),
    loadArchived = load,
    unarchiveProduct = unarchive,
    refresh = refresh,
    toast = toast,
    reauth = reauth,
)

class ArchivedViewModelTest {
    @Test fun load_success_sets_content() =
        runTest {
            val v = vm()
            v.load()
            v.state.value.shouldBeInstanceOf<ArchivedUiState.Content>()
        }

    @Test fun load_failure_sets_error() =
        runTest {
            val v = vm(load = { RpcOutcome.Failure(RpcError.Internal("x")) })
            v.load()
            v.state.value.shouldBeInstanceOf<ArchivedUiState.Error>()
        }

    @Test fun unarchive_success_reloads() =
        runTest {
            var loads = 0
            val v =
                vm(load = {
                    loads++
                    RpcOutcome.Success(Products(emptyList()))
                })
            v.load()
            v.unarchive(ProductId.create())
            loads shouldBe 2
        }

    @Test fun unarchive_unauthorized_requests_reauth() =
        runTest {
            var n = 0
            val reauth = ReauthController()
            val job = launch { reauth.signal.collect { n++ } }
            runCurrent()
            val v = vm(unarchive = { RpcOutcome.Failure(RpcError.Unauthorized("x")) }, reauth = reauth)
            v.unarchive(ProductId.create())
            runCurrent()
            n shouldBe 1
            job.cancel()
        }
}
