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
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.frontend.app.shell.LocalIsWideShell
import net.brightroom.mindstock.frontend.core.ui.InventoryRefreshController
import net.brightroom.mindstock.frontend.feature.inventory.InventoryViewModel

/**
 * 在庫ホーム + カードからの補充/消費シートを束ねる live エントリ。
 * 商品詳細はオーバーレイ（app 層）へ昇格したため、ここでは onOpenProduct を上げるだけ。
 */
@Composable
fun InventoryRoute(
    homeViewModel: InventoryViewModel,
    refresh: InventoryRefreshController,
    onOpenProduct: (ProductId, Stock?) -> Unit,
    onAddProduct: () -> Unit,
    displayName: String = "",
    householdName: String = "",
    memberCount: Int = 1,
    onShop: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val state by homeViewModel.state.collectAsState()
    var moveTarget by remember { mutableStateOf<Pair<Stock, MoveMode>?>(null) }
    val wide = LocalIsWideShell.current

    LaunchedEffect(Unit) { homeViewModel.load() }
    LaunchedEffect(refresh) { refresh.signal.collect { homeViewModel.load() } }

    StockHomeScreen(
        state = state,
        wide = wide,
        displayName = displayName,
        householdName = householdName,
        memberCount = memberCount,
        onSelectView = { homeViewModel.setView(it) },
        onQueryChange = { homeViewModel.setQuery(it) },
        onOpen = { stock -> onOpenProduct(stock.product.id, stock) },
        onReplenish = { moveTarget = it to MoveMode.Replenish },
        onConsume = { moveTarget = it to MoveMode.Consume },
        onAddProduct = onAddProduct,
        onShop = onShop,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
    )

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
            moveTarget = null
        },
    )
}
