package net.brightroom.mindstock.frontend.feature.inventory.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.loading
import mindstock.frontend.generated.resources.stock_view_grid
import mindstock.frontend.generated.resources.stock_view_list
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.StockStatus
import net.brightroom.mindstock.frontend.core.ui.resolve
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.SegOption
import net.brightroom.mindstock.frontend.designsystem.atom.SegmentedControl
import net.brightroom.mindstock.frontend.designsystem.atom.StatusDot
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.feature.inventory.InventoryUiState
import net.brightroom.mindstock.frontend.feature.inventory.StockView
import org.jetbrains.compose.resources.stringResource

@Composable
fun StockHomeScreen(
    state: InventoryUiState,
    onSelectView: (StockView) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (state) {
            is InventoryUiState.Loading -> {
                AppText(stringResource(Res.string.loading))
            }

            is InventoryUiState.Error -> {
                AppText(state.text.resolve())
            }

            is InventoryUiState.Content -> {
                SegmentedControl(
                    options =
                        listOf(
                            SegOption(StockView.List.name, stringResource(Res.string.stock_view_list)),
                            SegOption(StockView.Grid.name, stringResource(Res.string.stock_view_grid)),
                        ),
                    selectedKey = state.view.name,
                    onSelect = { onSelectView(StockView.valueOf(it)) },
                )
                StockList(state)
            }
        }
    }
}

@Composable
private fun StockList(content: InventoryUiState.Content) {
    LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(content.stocks.list) { stock -> StockRow(stock) }
    }
}

@Composable
private fun StockRow(stock: Stock) {
    val tokens = LocalMindstockTokens.current
    val statusColor =
        when (stock.status()) {
            StockStatus.在庫切れ -> tokens.statusOut
            StockStatus.残りわずか -> tokens.statusLow
            StockStatus.十分 -> tokens.statusOk
        }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color = statusColor)
        AppText(stock.product.name())
        AppText(stock.currentQuantity().toString())
    }
}
