package net.brightroom.mindstock.frontend.feature.shopping.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.action_replenish
import mindstock.frontend.generated.resources.loading
import mindstock.frontend.generated.resources.shop_add_from_stock_sub
import mindstock.frontend.generated.resources.shop_add_from_stock_title
import mindstock.frontend.generated.resources.shop_empty_sub
import mindstock.frontend.generated.resources.shop_empty_title
import mindstock.frontend.generated.resources.shop_manual_badge
import mindstock.frontend.generated.resources.shop_progress
import mindstock.frontend.generated.resources.shop_progress_count
import mindstock.frontend.generated.resources.shop_remove
import mindstock.frontend.generated.resources.shop_section_auto
import mindstock.frontend.generated.resources.shop_section_manual
import mindstock.frontend.generated.resources.shop_subtitle
import mindstock.frontend.generated.resources.shop_title
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingEntry
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.frontend.core.ui.resolve
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.PrimaryButton
import net.brightroom.mindstock.frontend.feature.inventory.ui.MoveMode
import net.brightroom.mindstock.frontend.feature.inventory.ui.MoveSheet
import net.brightroom.mindstock.frontend.feature.shopping.ShoppingListUiState
import org.jetbrains.compose.resources.stringResource

@Composable
fun ShoppingListScreen(
    state: ShoppingListUiState,
    onOpenProduct: (ProductId, Stock?) -> Unit,
    onSetWanted: (ProductId, Boolean) -> Unit,
    onReplenish: (ProductId, Int, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var addOpen by remember { mutableStateOf(false) }
    var moveTarget by remember { mutableStateOf<Stock?>(null) }
    val done = remember { mutableStateMapOf<String, Boolean>() }

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppText(stringResource(Res.string.shop_subtitle))
        AppText(stringResource(Res.string.shop_title))

        // 在庫から探して追加
        PrimaryButton(onClick = { addOpen = true }) { AppText(stringResource(Res.string.shop_add_from_stock_title)) }
        AppText(stringResource(Res.string.shop_add_from_stock_sub))

        when (state) {
            is ShoppingListUiState.Loading -> {
                AppText(stringResource(Res.string.loading))
            }

            is ShoppingListUiState.Error -> {
                AppText(state.text.resolve())
            }

            is ShoppingListUiState.Content -> {
                val auto = state.auto().list
                val manual = state.manual().list
                val items = auto + manual
                if (items.isEmpty()) {
                    AppText(stringResource(Res.string.shop_empty_title))
                    AppText(stringResource(Res.string.shop_empty_sub))
                } else {
                    val total = items.size
                    val remaining =
                        items.count {
                            done[
                                it.stock.product.id
                                    .toString(),
                            ] != true
                        }
                    AppText(stringResource(Res.string.shop_progress, remaining))
                    AppText(stringResource(Res.string.shop_progress_count, total - remaining, total))
                    LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (auto.isNotEmpty()) {
                            if (manual.isNotEmpty()) {
                                item { AppText(stringResource(Res.string.shop_section_auto)) }
                            }
                            items(auto) { entry ->
                                ShopRow(entry, isManual = false, done, onOpenProduct, onSetWanted, onReplenish = {
                                    moveTarget =
                                        entry.stock
                                })
                            }
                        }
                        if (manual.isNotEmpty()) {
                            item { AppText(stringResource(Res.string.shop_section_manual)) }
                            items(manual) { entry ->
                                ShopRow(entry, isManual = true, done, onOpenProduct, onSetWanted, onReplenish = {
                                    moveTarget =
                                        entry.stock
                                })
                            }
                        }
                    }
                }
            }
        }
    }

    AddToListSheet(
        open = addOpen,
        candidates = (state as? ShoppingListUiState.Content)?.addable()?.list ?: emptyList(),
        onClose = { addOpen = false },
        onAdd = { pid -> onSetWanted(pid, true) },
    )

    val mt = moveTarget
    MoveSheet(
        open = mt != null,
        mode = MoveMode.Replenish,
        stock = mt,
        onClose = { moveTarget = null },
        onSubmit = { quantity, note ->
            val s = mt ?: return@MoveSheet
            onReplenish(s.product.id, quantity, note)
            moveTarget = null
        },
    )
}

@Composable
private fun ShopRow(
    entry: ShoppingEntry,
    isManual: Boolean,
    done: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Boolean>,
    onOpenProduct: (ProductId, Stock?) -> Unit,
    onSetWanted: (ProductId, Boolean) -> Unit,
    onReplenish: () -> Unit,
) {
    val pid = entry.stock.product.id
    val key = pid.toString()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PrimaryButton(onClick = { done[key] = !(done[key] ?: false) }) { AppText(if (done[key] == true) "✓" else "○") }
        PrimaryButton(onClick = { onOpenProduct(pid, entry.stock) }) { AppText(entry.stock.product.name()) }
        if (isManual) {
            AppText(stringResource(Res.string.shop_manual_badge))
            PrimaryButton(onClick = { onSetWanted(pid, false) }) { AppText(stringResource(Res.string.shop_remove)) }
        }
        PrimaryButton(onClick = onReplenish) { AppText(stringResource(Res.string.action_replenish)) }
    }
}
