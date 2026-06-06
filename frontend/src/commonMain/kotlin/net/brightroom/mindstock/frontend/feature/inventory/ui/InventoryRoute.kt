package net.brightroom.mindstock.frontend.feature.inventory.ui

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
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Reason
import net.brightroom.mindstock.frontend.feature.inventory.InventoryUiState
import net.brightroom.mindstock.frontend.feature.inventory.InventoryViewModel
import net.brightroom.mindstock.frontend.feature.inventory.ProductDetailViewModel

/**
 * 在庫ホーム + 商品詳細 + 補充/消費/訂正シートの表示状態を束ねる live エントリ。
 * ViewModel の生成は呼び出し側（App）から factory で受ける（householdId 注入・テスト容易性）。
 */
@Composable
fun InventoryRoute(
    homeViewModel: InventoryViewModel,
    detailViewModelFactory: (Stock) -> ProductDetailViewModel,
    onAddProduct: () -> Unit,
    displayName: String = "",
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val state by homeViewModel.state.collectAsState()
    var selected by remember { mutableStateOf<Stock?>(null) }
    var moveTarget by remember { mutableStateOf<Pair<Stock, MoveMode>?>(null) }

    LaunchedEffect(Unit) { homeViewModel.load() }

    val current = selected
    if (current == null) {
        StockHomeScreen(
            state = state,
            displayName = displayName,
            onSelectView = { homeViewModel.setView(it) },
            onQueryChange = { homeViewModel.setQuery(it) },
            onOpen = { selected = it },
            onReplenish = { moveTarget = it to MoveMode.Replenish },
            onConsume = { moveTarget = it to MoveMode.Consume },
            onAddProduct = onAddProduct,
            modifier = modifier,
        )
    } else {
        val detailVm = remember(current) { detailViewModelFactory(current) }
        val detailState by detailVm.state.collectAsState()
        LaunchedEffect(current) { detailVm.load() }
        ProductDetailScreen(
            stock = current,
            detail = detailState,
            onBack = { selected = null },
            onReplenish = { moveTarget = it to MoveMode.Replenish },
            onConsume = { moveTarget = it to MoveMode.Consume },
            onCorrect = { target: MovementId, qty: Int, reason: String ->
                scope.launch {
                    detailVm.correct(target, Quantity(qty), Reason(reason))
                    homeViewModel.load()
                    val refreshed =
                        (homeViewModel.state.value as? InventoryUiState.Content)
                            ?.stocks
                            ?.list
                            ?.firstOrNull { it.product.id == current.product.id }
                    selected = refreshed
                }
            },
            modifier = modifier,
        )
    }

    val mt = moveTarget
    MoveSheet(
        open = mt != null,
        mode = mt?.second ?: MoveMode.Replenish,
        stock = mt?.first,
        onClose = { moveTarget = null },
        onSubmit = { quantity, note ->
            val (stock, mode) = mt ?: return@MoveSheet
            scope.launch {
                when (mode) {
                    MoveMode.Replenish -> homeViewModel.replenish(stock.product.id, Quantity(quantity), Note(note))
                    MoveMode.Consume -> homeViewModel.consume(stock.product.id, Quantity(quantity), Note(note))
                }
            }
            // 補充/消費後は詳細の Stock が陳腐化する（数量は home の Stocks 由来）。
            // 安全な既定として home に戻し、再フェッチ済みの最新数量を見せる。
            selected = null
        },
    )
}
