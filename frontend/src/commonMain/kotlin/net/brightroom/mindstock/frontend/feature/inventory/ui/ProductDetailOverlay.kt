package net.brightroom.mindstock.frontend.feature.inventory.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Reason
import net.brightroom.mindstock.frontend.core.ui.InventoryRefreshController
import net.brightroom.mindstock.frontend.feature.inventory.ProductDetailUiState
import net.brightroom.mindstock.frontend.feature.inventory.ProductDetailViewModel

/** app 層から開く商品詳細オーバーレイのターゲット。 */
data class DetailTarget(
    val productId: ProductId,
    val seed: Stock?,
)

@Composable
fun ProductDetailOverlay(
    target: DetailTarget,
    viewModelFactory: (DetailTarget) -> ProductDetailViewModel,
    refresh: InventoryRefreshController,
    onBack: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val vm = remember(target) { viewModelFactory(target) }
    val state by vm.state.collectAsState()
    var moveMode by remember(target) { mutableStateOf<MoveMode?>(null) }

    LaunchedEffect(target) { vm.load() }
    // 他タブ由来の変更（裏で発火）を詳細にも反映
    LaunchedEffect(target) { refresh.signal.collect { vm.load() } }

    ProductDetailScreen(
        detail = state,
        seed = target.seed,
        onBack = onBack,
        onReplenish = { moveMode = MoveMode.Replenish },
        onConsume = { moveMode = MoveMode.Consume },
        onCorrect = { mid: MovementId, qty: Int, reason: String ->
            scope.launch { vm.correct(mid, Quantity(qty), Reason(reason)) }
        },
        onToggleWanted = { wanted -> scope.launch { vm.setWanted(target.productId, wanted) } },
        onOpenSettings = onOpenSettings,
        modifier = modifier.fillMaxSize(),
    )

    val mode = moveMode
    // MoveSheet は対象 Stock が要る。Content の stock を使う（無ければ seed）。
    val stock: Stock? = (state as? ProductDetailUiState.Content)?.stock ?: target.seed
    MoveSheet(
        open = mode != null && stock != null,
        mode = mode ?: MoveMode.Replenish,
        stock = stock,
        onClose = { moveMode = null },
        onSubmit = { quantity, note, occurredAt ->
            val m = mode ?: return@MoveSheet
            scope.launch {
                when (m) {
                    MoveMode.Replenish -> vm.replenish(Quantity(quantity), Note(note), occurredAt)
                    MoveMode.Consume -> vm.consume(Quantity(quantity), Note(note), occurredAt)
                }
            }
            moveMode = null
        },
    )
}
