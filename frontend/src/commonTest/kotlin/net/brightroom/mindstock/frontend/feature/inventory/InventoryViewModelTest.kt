package net.brightroom.mindstock.frontend.feature.inventory

import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.rpc.result.RpcError
import kotlin.test.Test

class InventoryViewModelTest {
    @Test
    fun load_success_sets_content() =
        runTest {
            val vm =
                InventoryViewModel(
                    householdId = HouseholdId.create(),
                    loadStocks = { RpcOutcome.Success(Stocks(emptyList())) },
                )
            vm.load()
            vm.state.value.shouldBeInstanceOf<InventoryUiState.Content>()
        }

    @Test
    fun load_failure_sets_error() =
        runTest {
            val vm =
                InventoryViewModel(
                    householdId = HouseholdId.create(),
                    loadStocks = { RpcOutcome.Failure(RpcError.Internal("boom")) },
                )
            vm.load()
            vm.state.value.shouldBeInstanceOf<InventoryUiState.Error>()
        }
}
