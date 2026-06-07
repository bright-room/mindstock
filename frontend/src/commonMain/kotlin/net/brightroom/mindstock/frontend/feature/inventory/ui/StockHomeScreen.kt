package net.brightroom.mindstock.frontend.feature.inventory.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import mindstock.frontend.generated.resources.loading
import mindstock.frontend.generated.resources.stock_add_product
import mindstock.frontend.generated.resources.stock_count_all
import mindstock.frontend.generated.resources.stock_greeting
import mindstock.frontend.generated.resources.stock_search_count
import mindstock.frontend.generated.resources.stock_search_empty
import mindstock.frontend.generated.resources.stock_search_placeholder
import mindstock.frontend.generated.resources.stock_title
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.frontend.core.ui.resolve
import net.brightroom.mindstock.frontend.designsystem.atom.AddTile
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.HouseholdPill
import net.brightroom.mindstock.frontend.designsystem.atom.NavIconButton
import net.brightroom.mindstock.frontend.designsystem.atom.SearchField
import net.brightroom.mindstock.frontend.designsystem.atom.SegOption
import net.brightroom.mindstock.frontend.designsystem.atom.SegmentedControl
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import net.brightroom.mindstock.frontend.feature.inventory.InventoryUiState
import net.brightroom.mindstock.frontend.feature.inventory.StockView
import net.brightroom.mindstock.frontend.feature.inventory.stockSummaryOf
import org.jetbrains.compose.resources.stringResource

@Composable
fun StockHomeScreen(
    state: InventoryUiState,
    wide: Boolean = false,
    displayName: String = "",
    householdName: String = "",
    memberCount: Int = 1,
    onSelectView: (StockView) -> Unit,
    onQueryChange: (String) -> Unit,
    onOpen: (Stock) -> Unit,
    onReplenish: (Stock) -> Unit,
    onConsume: (Stock) -> Unit,
    onAddProduct: () -> Unit,
    onShop: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMindstockTokens.current
    when (state) {
        is InventoryUiState.Loading -> {
            Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
                AppText(stringResource(Res.string.loading), color = tokens.sub)
            }
        }

        is InventoryUiState.Error -> {
            Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
                AppText(state.text.resolve(), color = tokens.statusOut)
            }
        }

        is InventoryUiState.Content -> {
            val visible = state.visibleStocks()
            val showEmpty = state.query.isNotBlank() && visible.list.isEmpty()
            val header: @Composable () -> Unit = {
                StockHeader(
                    state = state,
                    wide = wide,
                    displayName = displayName,
                    householdName = householdName,
                    memberCount = memberCount,
                    visibleCount = visible.list.size,
                    onSelectView = onSelectView,
                    onQueryChange = onQueryChange,
                    onShop = onShop,
                    onOpenSettings = onOpenSettings,
                )
            }
            // 画面全体を 1 つのスクローラに(モック準拠: ヘッダもリストと一緒にスクロール)。
            if (state.view == StockView.Grid) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp),
                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                ) {
                    item(key = "header", span = { GridItemSpan(maxLineSpan) }) { header() }
                    if (showEmpty) {
                        item(key = "empty", span = { GridItemSpan(maxLineSpan) }) { SearchEmpty(state.query, tokens) }
                    } else {
                        items(visible.list) { stock ->
                            CompactCard(stock = stock, onOpen = onOpen, onReplenish = onReplenish, onConsume = onConsume)
                        }
                        if (state.query.isBlank()) {
                            item(key = "addtile", span = { GridItemSpan(maxLineSpan) }) {
                                AddTile(label = stringResource(Res.string.stock_add_product), onClick = onAddProduct)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp),
                ) {
                    item(key = "header") { header() }
                    if (showEmpty) {
                        item(key = "empty") { SearchEmpty(state.query, tokens) }
                    } else {
                        items(visible.list) { stock ->
                            ProductCard(stock = stock, onOpen = onOpen, onReplenish = onReplenish, onConsume = onConsume)
                        }
                        if (state.query.isBlank()) {
                            item(key = "addtile") {
                                AddTile(label = stringResource(Res.string.stock_add_product), onClick = onAddProduct)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StockHeader(
    state: InventoryUiState.Content,
    wide: Boolean,
    displayName: String,
    householdName: String,
    memberCount: Int,
    visibleCount: Int,
    onSelectView: (StockView) -> Unit,
    onQueryChange: (String) -> Unit,
    onShop: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val tokens = LocalMindstockTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 1.dp)) {
        // デスクトップ(サイドバーあり)では世帯ピル/ベルは出さない(サイドバーが担う)。
        if (!wide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HouseholdPill(name = householdName, memberCount = memberCount, onClick = onOpenSettings)
                NavIconButton(icon = AppIconName.Bell, contentDescription = "notifications", onClick = {}, badge = true)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AppText(stringResource(Res.string.stock_greeting, displayName), style = MindstockType.greeting(), color = tokens.sub)
            AppText(stringResource(Res.string.stock_title), style = MindstockType.screenTitle(), color = tokens.ink)
        }

        if (state.query.isBlank()) {
            val summary = stockSummaryOf(state.stocks.list.map { it.status() })
            SummaryStrip(summary = summary, onClick = onShop)
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
                    stringResource(Res.string.stock_search_count, visibleCount)
                } else {
                    stringResource(Res.string.stock_count_all, state.stocks.list.size)
                },
                style = MindstockType.sectionMeta(),
                color = tokens.sub,
            )
            SegmentedControl(
                options =
                    listOf(
                        SegOption(StockView.Grid.name, "", AppIconName.Grid),
                        SegOption(StockView.List.name, "", AppIconName.ListView),
                    ),
                selectedKey = state.view.name,
                onSelect = { onSelectView(StockView.valueOf(it)) },
                modifier = Modifier.width(96.dp),
            )
        }
    }
}

@Composable
private fun SearchEmpty(
    query: String,
    tokens: MindstockTokens,
) {
    AppText(
        stringResource(Res.string.stock_search_empty, query.trim()),
        style = MindstockType.cardTitle(),
        color = tokens.ink,
    )
}
