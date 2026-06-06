package net.brightroom.mindstock.frontend.feature.inventory

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.toast_corrected
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Reason
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.ui.ToastController
import kotlin.test.Test

private fun detailVm(
    loadHistory: suspend (ProductId) -> RpcOutcome<StockMovements> = { RpcOutcome.Success(StockMovements(emptyList())) },
    correct: suspend (MovementId, Quantity, Reason) -> RpcOutcome<Unit> = { _, _, _ -> RpcOutcome.Success(Unit) },
    toast: ToastController = ToastController(),
    reauth: ReauthController = ReauthController(),
) = ProductDetailViewModel(
    productId = ProductId.create(),
    loadHistory = loadHistory,
    correctMovement = correct,
    toast = toast,
    reauth = reauth,
)

class ProductDetailViewModelTest {
    @Test
    fun load_success_sets_content() =
        runTest {
            val v = detailVm()
            v.load()
            v.state.value.shouldBeInstanceOf<ProductDetailUiState.Content>()
        }

    @Test
    fun correct_success_refetches_and_toasts() =
        runTest {
            var loads = 0
            val toast = ToastController()
            val v =
                detailVm(loadHistory = {
                    loads++
                    RpcOutcome.Success(StockMovements(emptyList()))
                }, toast = toast)
            v.load()
            v.correct(MovementId(1), Quantity(3), Reason("数え間違い"))
            loads shouldBe 2
            toast.current.value
                ?.text
                ?.resource shouldBe Res.string.toast_corrected
        }
}
