package net.brightroom.mindstock.frontend.feature.activity

import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.stock.ActivityFeed
import kotlin.test.Test

private fun vm(
    loadActivity: suspend (HouseholdId) -> RpcOutcome<ActivityFeed> = { RpcOutcome.Success(ActivityFeed(emptyList())) },
    toast: ToastController = ToastController(),
    reauth: ReauthController = ReauthController(),
) = ActivityViewModel(
    householdId = HouseholdId.create(),
    loadActivity = loadActivity,
    toast = toast,
    reauth = reauth,
)

class ActivityViewModelTest {
    @Test
    fun load_success_sets_content() =
        runTest {
            val v = vm()
            v.load()
            v.state.value.shouldBeInstanceOf<ActivityUiState.Content>()
        }

    @Test
    fun load_failure_sets_error() =
        runTest {
            val v = vm(loadActivity = { RpcOutcome.Failure(RpcError.Internal("boom")) })
            v.load()
            v.state.value.shouldBeInstanceOf<ActivityUiState.Error>()
        }
}
