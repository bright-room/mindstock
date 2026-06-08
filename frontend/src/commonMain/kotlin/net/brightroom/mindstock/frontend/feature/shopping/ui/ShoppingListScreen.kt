package net.brightroom.mindstock.frontend.feature.shopping.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.LocalDateTime
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.action_replenish
import mindstock.frontend.generated.resources.forecast_days_left_plain
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
import mindstock.frontend.generated.resources.shop_shortage
import mindstock.frontend.generated.resources.shop_stock_qty
import mindstock.frontend.generated.resources.shop_subtitle
import mindstock.frontend.generated.resources.shop_title
import mindstock.frontend.generated.resources.status_low
import mindstock.frontend.generated.resources.status_ok
import mindstock.frontend.generated.resources.status_out
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingEntry
import net.brightroom.mindstock.domain.model.inventory.stock.ConsumptionForecast
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.StockStatus
import net.brightroom.mindstock.extensions.kotlinx.datetime.now
import net.brightroom.mindstock.frontend.core.ui.resolve
import net.brightroom.mindstock.frontend.designsystem.atom.AppButton
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.ButtonSize
import net.brightroom.mindstock.frontend.designsystem.atom.ButtonVariant
import net.brightroom.mindstock.frontend.designsystem.atom.EmptyState
import net.brightroom.mindstock.frontend.designsystem.atom.StatusDot
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import net.brightroom.mindstock.frontend.designsystem.theme.ShadowLevel
import net.brightroom.mindstock.frontend.designsystem.theme.softShadow
import net.brightroom.mindstock.frontend.feature.inventory.glyphForProductName
import net.brightroom.mindstock.frontend.feature.inventory.ui.MoveMode
import net.brightroom.mindstock.frontend.feature.inventory.ui.MoveSheet
import net.brightroom.mindstock.frontend.feature.shopping.ShoppingListUiState
import org.jetbrains.compose.resources.stringResource
import kotlin.math.max

@Composable
fun ShoppingListScreen(
    state: ShoppingListUiState,
    onOpenProduct: (ProductId, Stock?) -> Unit,
    onSetWanted: (ProductId, Boolean) -> Unit,
    onReplenish: (ProductId, Int, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMindstockTokens.current
    var addOpen by remember { mutableStateOf(false) }
    var moveTarget by remember { mutableStateOf<Stock?>(null) }
    val done = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AppText(stringResource(Res.string.shop_subtitle), style = MindstockType.greeting(), color = tokens.sub)
                AppText(stringResource(Res.string.shop_title), style = MindstockType.screenTitle(), color = tokens.ink)
            }
        }
        item { AddFromStockCard(onClick = { addOpen = true }) }

        when (state) {
            is ShoppingListUiState.Loading -> {
                item { AppText(stringResource(Res.string.loading), color = tokens.sub) }
            }

            is ShoppingListUiState.Error -> {
                item { AppText(state.text.resolve(), color = tokens.statusOut) }
            }

            is ShoppingListUiState.Content -> {
                val auto = state.auto().list
                val manual = state.manual().list
                val items = auto + manual
                if (items.isEmpty()) {
                    item {
                        EmptyState(
                            icon = AppIconName.Check,
                            title = stringResource(Res.string.shop_empty_title),
                            sub = stringResource(Res.string.shop_empty_sub),
                        )
                    }
                } else {
                    val total = items.size
                    val doneCount =
                        items.count {
                            done[
                                it.stock.product.id
                                    .toString(),
                            ] == true
                        }
                    item { ProgressBanner(remaining = total - doneCount, doneCount = doneCount, total = total) }
                    if (auto.isNotEmpty()) {
                        if (manual.isNotEmpty()) item { SectionLabel(stringResource(Res.string.shop_section_auto)) }
                        items(auto) { entry ->
                            ShopRow(entry, isManual = false, done, onOpenProduct, onSetWanted) { moveTarget = entry.stock }
                        }
                    }
                    if (manual.isNotEmpty()) {
                        item { SectionLabel(stringResource(Res.string.shop_section_manual)) }
                        items(manual) { entry ->
                            ShopRow(entry, isManual = true, done, onOpenProduct, onSetWanted) { moveTarget = entry.stock }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
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
        onSubmit = { quantity, note, _ ->
            val s = mt ?: return@MoveSheet
            onReplenish(s.product.id, quantity, note)
            moveTarget = null
        },
        showDatePicker = false,
    )
}

@Composable
private fun AddFromStockCard(onClick: () -> Unit) {
    val tokens = LocalMindstockTokens.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(tokens.surface)
                // mock 準拠: 破線ボーダー(1px dashed)
                .drawBehind {
                    drawRoundRect(
                        color = tokens.line,
                        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                        cornerRadius = CornerRadius(16.dp.toPx()),
                    )
                }.clickable(onClick = onClick)
                .padding(horizontal = 15.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(tokens.accentSoft),
            contentAlignment = Alignment.Center,
        ) { AppIcon(AppIconName.Search, contentDescription = null, tint = tokens.accent, size = 18.dp) }
        Column(Modifier.weight(1f)) {
            AppText(stringResource(Res.string.shop_add_from_stock_title), style = MindstockType.summaryTitle(), color = tokens.ink)
            Spacer(Modifier.height(3.dp))
            AppText(stringResource(Res.string.shop_add_from_stock_sub), style = MindstockType.summarySub(), color = tokens.faint)
        }
        AppIcon(AppIconName.Plus, contentDescription = null, tint = tokens.faint, size = 18.dp)
    }
}

@Composable
private fun ProgressBanner(
    remaining: Int,
    doneCount: Int,
    total: Int,
) {
    val tokens = LocalMindstockTokens.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .softShadow(ShadowLevel.Md, shape)
                .clip(shape)
                .background(tokens.accent)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(AppIconName.Cart, contentDescription = null, tint = tokens.onAccent, size = 22.dp)
        AppText(
            stringResource(Res.string.shop_progress, remaining),
            style = MindstockType.summaryTitle(),
            color = tokens.onAccent,
            modifier = Modifier.weight(1f),
        )
        AppText(
            stringResource(Res.string.shop_progress_count, doneCount, total),
            style = MindstockType.summaryTitle(),
            color = tokens.onAccent,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    val tokens = LocalMindstockTokens.current
    AppText(
        text,
        style = MindstockType.statusLabel().copy(fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 12.sp),
        color = tokens.faint,
        modifier = Modifier.padding(start = 4.dp),
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
    val tokens = LocalMindstockTokens.current
    val stock = entry.stock
    val forecast = stock.forecast(LocalDateTime.now())
    val pid = stock.product.id
    val key = pid.toString()
    val isDone = done[key] == true
    val status = stock.status()
    val statusColor =
        when (status) {
            StockStatus.在庫切れ -> tokens.statusOut
            StockStatus.残りわずか -> tokens.statusLow
            StockStatus.十分 -> tokens.statusOk
        }
    val statusSoft =
        when (status) {
            StockStatus.在庫切れ -> tokens.statusOutSoft
            StockStatus.残りわずか -> tokens.statusLowSoft
            StockStatus.十分 -> tokens.statusOkSoft
        }
    val statusLabel =
        when (status) {
            StockStatus.在庫切れ -> stringResource(Res.string.status_out)
            StockStatus.残りわずか -> stringResource(Res.string.status_low)
            StockStatus.十分 -> stringResource(Res.string.status_ok)
        }
    val qty = stock.currentQuantity()
    val min = stock.product.setting.minimumStock()
    val unit = stock.product.setting.unit()
    val shortage = max(1, min - qty + if (status == StockStatus.在庫切れ) min else 0)
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(if (isDone) 0.5f else 1f)
                .let { if (isDone) it else it.softShadow(ShadowLevel.Sm, shape) }
                .clip(shape)
                .background(tokens.surface)
                .border(1.dp, tokens.lineSoft, shape)
                .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckCircle(checked = isDone, onToggle = { done[key] = !isDone })
        Column(
            modifier = Modifier.weight(1f).clickable { onOpenProduct(pid, stock) },
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            AppText(
                stock.product.name(),
                style = MindstockType.cardTitle(),
                color = tokens.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (isManual) {
                    Box(
                        modifier =
                            Modifier
                                .clip(
                                    RoundedCornerShape(6.dp),
                                ).background(tokens.accentSoft)
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                    ) {
                        AppText(
                            stringResource(Res.string.shop_manual_badge),
                            style = MindstockType.statusLabel().copy(fontSize = 10.5f.sp, lineHeight = 10.5f.sp),
                            color = tokens.accent,
                        )
                    }
                    AppText(stringResource(Res.string.shop_stock_qty, qty, unit), style = MindstockType.summarySub(), color = tokens.faint)
                } else {
                    StatusDot(color = statusColor, soft = statusSoft, label = statusLabel)
                    AppText(
                        "· " + stringResource(Res.string.shop_shortage, shortage, unit),
                        style = MindstockType.summarySub(),
                        color = tokens.faint,
                    )
                    if (forecast is ConsumptionForecast.DaysRemaining) {
                        AppText(
                            stringResource(Res.string.forecast_days_left_plain, forecast.days),
                            style = MindstockType.statusLabel().copy(fontWeight = FontWeight.SemiBold),
                            color = tokens.accent,
                        )
                    }
                }
            }
        }
        if (isManual) {
            Box(
                modifier =
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(tokens.surface)
                        .border(1.dp, tokens.line, RoundedCornerShape(9.dp))
                        .clickable { onSetWanted(pid, false) },
                contentAlignment = Alignment.Center,
            ) { AppIcon(AppIconName.Close, contentDescription = null, tint = tokens.faint, size = 15.dp) }
        }
        AppButton(
            onClick = onReplenish,
            variant = ButtonVariant.Soft,
            size = ButtonSize.Sm,
            icon = AppIconName.Plus,
        ) { AppText(stringResource(Res.string.action_replenish)) }
    }
}

@Composable
private fun CheckCircle(
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val tokens = LocalMindstockTokens.current
    Box(
        modifier =
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(if (checked) tokens.accent else tokens.surface)
                .border(2.dp, if (checked) tokens.accent else tokens.line, RoundedCornerShape(99.dp))
                .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) { if (checked) AppIcon(AppIconName.Check, contentDescription = null, tint = tokens.onAccent, size = 16.dp) }
}
