package net.brightroom.mindstock.frontend.feature.inventory.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.household_default_name
import mindstock.frontend.generated.resources.loading
import mindstock.frontend.generated.resources.stock_add_product
import mindstock.frontend.generated.resources.stock_count_all
import mindstock.frontend.generated.resources.stock_greeting
import mindstock.frontend.generated.resources.stock_search_count
import mindstock.frontend.generated.resources.stock_search_empty
import mindstock.frontend.generated.resources.stock_search_placeholder
import mindstock.frontend.generated.resources.stock_title
import mindstock.frontend.generated.resources.stock_view_grid
import mindstock.frontend.generated.resources.stock_view_list
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.frontend.core.ui.resolve
import net.brightroom.mindstock.frontend.designsystem.atom.AddTile
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.HouseholdPill
import net.brightroom.mindstock.frontend.designsystem.atom.RoundBtn
import net.brightroom.mindstock.frontend.designsystem.atom.SearchField
import net.brightroom.mindstock.frontend.designsystem.atom.SegOption
import net.brightroom.mindstock.frontend.designsystem.atom.SegmentedControl
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import net.brightroom.mindstock.frontend.feature.inventory.InventoryUiState
import net.brightroom.mindstock.frontend.feature.inventory.StockView
import net.brightroom.mindstock.frontend.feature.inventory.stockSummaryOf
import org.jetbrains.compose.resources.stringResource

@Composable
fun StockHomeScreen(
    state: InventoryUiState,
    displayName: String = "",
    onSelectView: (StockView) -> Unit,
    onQueryChange: (String) -> Unit,
    onOpen: (Stock) -> Unit,
    onReplenish: (Stock) -> Unit,
    onConsume: (Stock) -> Unit,
    onAddProduct: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMindstockTokens.current
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (state) {
            is InventoryUiState.Loading -> {
                AppText(stringResource(Res.string.loading), color = tokens.sub)
            }

            is InventoryUiState.Error -> {
                AppText(state.text.resolve(), color = tokens.statusOut)
            }

            is InventoryUiState.Content -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 世帯名/人数は P6-1b で session から渡す。本パスは既定文言で表示のみ。
                    HouseholdPill(
                        name = stringResource(Res.string.household_default_name),
                        memberCount = 1,
                        onClick = {},
                    )
                    RoundBtn(icon = AppIconName.Bell, contentDescription = "notifications", onClick = {})
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppText(stringResource(Res.string.stock_greeting, displayName), style = MindstockType.greeting(), color = tokens.sub)
                    AppText(stringResource(Res.string.stock_title), style = MindstockType.screenTitle(), color = tokens.ink)
                }

                val visible = state.visibleStocks()
                if (state.query.isBlank()) {
                    val summary = stockSummaryOf(state.stocks.list.map { it.status() })
                    SummaryStrip(summary = summary, onClick = {})
                }

                SearchField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    placeholder = stringResource(Res.string.stock_search_placeholder),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText(
                        if (state.query.isNotBlank()) {
                            stringResource(Res.string.stock_search_count, visible.list.size)
                        } else {
                            stringResource(Res.string.stock_count_all, state.stocks.list.size)
                        },
                        style = MindstockType.sectionMeta(),
                        color = tokens.sub,
                    )
                    SegmentedControl(
                        options =
                            listOf(
                                SegOption(StockView.List.name, stringResource(Res.string.stock_view_list)),
                                SegOption(StockView.Grid.name, stringResource(Res.string.stock_view_grid)),
                            ),
                        selectedKey = state.view.name,
                        onSelect = { onSelectView(StockView.valueOf(it)) },
                        modifier = Modifier.width(120.dp),
                    )
                }

                if (state.query.isNotBlank() && visible.list.isEmpty()) {
                    AppText(
                        stringResource(Res.string.stock_search_empty, state.query.trim()),
                        style = MindstockType.cardTitle(),
                        color = tokens.ink,
                    )
                } else if (state.view == StockView.Grid) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(13.dp),
                        horizontalArrangement = Arrangement.spacedBy(13.dp),
                    ) {
                        items(visible.list) { stock ->
                            CompactCard(stock = stock, onOpen = onOpen, onReplenish = onReplenish, onConsume = onConsume)
                        }
                        if (state.query.isBlank()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                AddTile(label = stringResource(Res.string.stock_add_product), onClick = onAddProduct)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(13.dp),
                    ) {
                        items(visible.list) { stock ->
                            ProductCard(stock = stock, onOpen = onOpen, onReplenish = onReplenish, onConsume = onConsume)
                        }
                        if (state.query.isBlank()) {
                            item {
                                AddTile(label = stringResource(Res.string.stock_add_product), onClick = onAddProduct)
                            }
                        }
                    }
                }
            }
        }
    }
}
