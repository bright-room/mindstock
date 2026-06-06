package net.brightroom.mindstock.frontend.feature.inventory.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.loading
import mindstock.frontend.generated.resources.stock_add_product
import mindstock.frontend.generated.resources.stock_search_placeholder
import mindstock.frontend.generated.resources.stock_view_grid
import mindstock.frontend.generated.resources.stock_view_list
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.frontend.core.ui.resolve
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.PrimaryButton
import net.brightroom.mindstock.frontend.designsystem.atom.SegOption
import net.brightroom.mindstock.frontend.designsystem.atom.SegmentedControl
import net.brightroom.mindstock.frontend.designsystem.atom.TextInput
import net.brightroom.mindstock.frontend.feature.inventory.InventoryUiState
import net.brightroom.mindstock.frontend.feature.inventory.StockView
import org.jetbrains.compose.resources.stringResource

@Composable
fun StockHomeScreen(
    state: InventoryUiState,
    onSelectView: (StockView) -> Unit,
    onQueryChange: (String) -> Unit,
    onOpen: (Stock) -> Unit,
    onReplenish: (Stock) -> Unit,
    onConsume: (Stock) -> Unit,
    onAddProduct: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (state) {
            is InventoryUiState.Loading -> AppText(stringResource(Res.string.loading))
            is InventoryUiState.Error -> AppText(state.text.resolve())
            is InventoryUiState.Content -> {
                TextInput(
                    value = state.query,
                    onValueChange = onQueryChange,
                    placeholder = stringResource(Res.string.stock_search_placeholder),
                    modifier = Modifier.fillMaxWidth(),
                )
                SegmentedControl(
                    options =
                        listOf(
                            SegOption(StockView.List.name, stringResource(Res.string.stock_view_list)),
                            SegOption(StockView.Grid.name, stringResource(Res.string.stock_view_grid)),
                        ),
                    selectedKey = state.view.name,
                    onSelect = { onSelectView(StockView.valueOf(it)) },
                )
                val visible = state.visibleStocks()
                if (state.view == StockView.Grid) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(visible.list) { stock ->
                            ProductCard(stock = stock, onOpen = onOpen, onReplenish = onReplenish, onConsume = onConsume)
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(visible.list) { stock ->
                            ProductCard(stock = stock, onOpen = onOpen, onReplenish = onReplenish, onConsume = onConsume)
                        }
                    }
                }
                PrimaryButton(onClick = onAddProduct) { AppText(stringResource(Res.string.stock_add_product)) }
            }
        }
    }
}
