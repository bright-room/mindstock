package net.brightroom.mindstock.frontend.feature.inventory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.action_back
import mindstock.frontend.generated.resources.action_consume
import mindstock.frontend.generated.resources.action_correct
import mindstock.frontend.generated.resources.action_replenish
import mindstock.frontend.generated.resources.action_settings
import mindstock.frontend.generated.resources.correct_reason_placeholder
import mindstock.frontend.generated.resources.correct_submit
import mindstock.frontend.generated.resources.correct_title
import mindstock.frontend.generated.resources.detail_history
import mindstock.frontend.generated.resources.detail_history_empty
import mindstock.frontend.generated.resources.detail_min_stock
import mindstock.frontend.generated.resources.detail_wanted_add
import mindstock.frontend.generated.resources.detail_wanted_auto
import mindstock.frontend.generated.resources.detail_wanted_remove
import mindstock.frontend.generated.resources.history_consume
import mindstock.frontend.generated.resources.history_corrected_badge
import mindstock.frontend.generated.resources.history_reason_label
import mindstock.frontend.generated.resources.history_replenish
import mindstock.frontend.generated.resources.history_time_date
import mindstock.frontend.generated.resources.history_time_days
import mindstock.frontend.generated.resources.history_time_hours
import mindstock.frontend.generated.resources.history_time_now
import mindstock.frontend.generated.resources.loading
import mindstock.frontend.generated.resources.status_low
import mindstock.frontend.generated.resources.status_ok
import mindstock.frontend.generated.resources.status_out
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.StockStatus
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementIdentity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement
import net.brightroom.mindstock.frontend.core.ui.resolve
import net.brightroom.mindstock.frontend.designsystem.atom.AppButton
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.ButtonVariant
import net.brightroom.mindstock.frontend.designsystem.atom.NavIconButton
import net.brightroom.mindstock.frontend.designsystem.atom.PrimaryButton
import net.brightroom.mindstock.frontend.designsystem.atom.Sheet
import net.brightroom.mindstock.frontend.designsystem.atom.StatusDot
import net.brightroom.mindstock.frontend.designsystem.atom.Stepper
import net.brightroom.mindstock.frontend.designsystem.atom.StockLevelBar
import net.brightroom.mindstock.frontend.designsystem.atom.TextInput
import net.brightroom.mindstock.frontend.designsystem.atom.Thumb
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import net.brightroom.mindstock.frontend.feature.inventory.ProductDetailUiState
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

@Composable
fun ProductDetailScreen(
    detail: ProductDetailUiState,
    seed: Stock?,
    onBack: () -> Unit,
    onReplenish: () -> Unit,
    onConsume: () -> Unit,
    onCorrect: (target: MovementId, quantity: Int, reason: String) -> Unit,
    onToggleWanted: (wanted: Boolean) -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val stock: Stock? = (detail as? ProductDetailUiState.Content)?.stock ?: seed
    val wanted: Boolean? = (detail as? ProductDetailUiState.Content)?.wanted
    val tokens = LocalMindstockTokens.current

    Column(modifier = modifier.fillMaxSize().background(tokens.bg)) {
        // ヘッダ(固定): 戻る + 設定
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavIconButton(AppIconName.Back, contentDescription = stringResource(Res.string.action_back), onClick = onBack)
            Spacer(Modifier.weight(1f))
            if (onOpenSettings != null) {
                NavIconButton(
                    AppIconName.Settings,
                    contentDescription = stringResource(Res.string.action_settings),
                    onClick = onOpenSettings,
                )
            }
        }

        if (stock == null) {
            val msg = (detail as? ProductDetailUiState.Error)?.text?.resolve() ?: stringResource(Res.string.loading)
            AppText(msg, modifier = Modifier.padding(20.dp), color = tokens.sub)
            return@Column
        }

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
        val unit = stock.product.setting.unit()

        // 本文(スクロール)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // サムネ + 商品名(中央寄せ)
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Thumb(icon = AppIconName.Box, size = 72.dp, radius = 22.dp)
                AppText(
                    stock.product.name(),
                    style = MindstockType.summaryTitle().copy(fontSize = 19.sp),
                    color = tokens.ink,
                    textAlign = TextAlign.Center,
                )
            }

            // 在庫カード
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(tokens.radiusLg))
                        .background(tokens.surface)
                        .border(1.dp, tokens.lineSoft, RoundedCornerShape(tokens.radiusLg))
                        .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            AppText(
                                "${stock.currentQuantity()}",
                                style = MindstockType.bigQty().copy(fontSize = 46.sp),
                                color = if (status == StockStatus.在庫切れ) tokens.statusOut else tokens.ink,
                            )
                            Spacer(Modifier.width(6.dp))
                            AppText(
                                unit,
                                style = MindstockType.unitCaption().copy(fontSize = 16.sp),
                                color = tokens.faint,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                        StatusDot(color = statusColor, soft = statusSoft, label = statusLabel)
                    }
                    Spacer(Modifier.weight(1f))
                    AppText(
                        stringResource(Res.string.detail_min_stock, stock.product.setting.minimumStock(), unit),
                        style = MindstockType.unitCaption().copy(fontSize = 12.5f.sp),
                        color = tokens.faint,
                    )
                }
                StockLevelBar(qty = stock.currentQuantity(), min = stock.product.setting.minimumStock(), color = statusColor)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppButton(
                        onClick = onReplenish,
                        variant = ButtonVariant.Soft,
                        icon = AppIconName.Plus,
                        modifier = Modifier.weight(1f),
                    ) { AppText(stringResource(Res.string.action_replenish)) }
                    AppButton(
                        onClick = onConsume,
                        variant = ButtonVariant.Ghost,
                        icon = AppIconName.Minus,
                        modifier = Modifier.weight(1f),
                    ) { AppText(stringResource(Res.string.action_consume)) }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(tokens.lineSoft))
                WantedSection(status = status, wanted = wanted, tokens = tokens, onToggleWanted = onToggleWanted)
            }

            // 履歴
            AppText(
                stringResource(Res.string.detail_history),
                style = MindstockType.sectionMeta().copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                color = tokens.ink,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
            HistorySection(detail = detail, unit = unit, onCorrect = onCorrect, tokens = tokens)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun WantedSection(
    status: StockStatus,
    wanted: Boolean?,
    tokens: MindstockTokens,
    onToggleWanted: (Boolean) -> Unit,
) {
    when {
        status != StockStatus.十分 -> {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(tokens.radiusMd))
                        .background(tokens.surface2)
                        .padding(horizontal = 13.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(AppIconName.Cart, contentDescription = null, tint = tokens.sub, size = 18.dp)
                AppText(stringResource(Res.string.detail_wanted_auto), style = MindstockType.statusLabel(), color = tokens.sub)
            }
        }

        wanted == true -> {
            AppButton(
                onClick = { onToggleWanted(false) },
                variant = ButtonVariant.Soft,
                icon = AppIconName.Cart,
                modifier = Modifier.fillMaxWidth(),
            ) { AppText(stringResource(Res.string.detail_wanted_remove)) }
        }

        wanted == false -> {
            AppButton(
                onClick = { onToggleWanted(true) },
                variant = ButtonVariant.Ghost,
                icon = AppIconName.Cart,
                modifier = Modifier.fillMaxWidth(),
            ) { AppText(stringResource(Res.string.detail_wanted_add)) }
        }

        else -> {
            Unit
        }
    }
}

@Composable
private fun HistorySection(
    detail: ProductDetailUiState,
    unit: String,
    onCorrect: (target: MovementId, quantity: Int, reason: String) -> Unit,
    tokens: MindstockTokens,
) {
    when (detail) {
        is ProductDetailUiState.Loading -> {
            AppText(stringResource(Res.string.loading), color = tokens.faint)
        }

        is ProductDetailUiState.Error -> {
            AppText(detail.text.resolve(), color = tokens.faint)
        }

        is ProductDetailUiState.Content -> {
            // 訂正イベントは行に積まず、対象の元行を「訂正済」として見せる(モック準拠・append-only を表示で畳む)。
            val byTarget =
                detail.movements.list
                    .filterIsInstance<StockMovement.Correction>()
                    .associateBy { it.target }
            val rows =
                detail.movements.list
                    .filter { it is StockMovement.Replenishment || it is StockMovement.Consumption }
                    .reversed()
            if (rows.isEmpty()) {
                AppText(stringResource(Res.string.detail_history_empty), color = tokens.faint, modifier = Modifier.padding(start = 4.dp))
            } else {
                val now = remember { Clock.System.now() }
                var correcting by remember { mutableStateOf<StockMovement?>(null) }
                Column {
                    rows.forEachIndexed { i, m ->
                        val correction = (m.identity as? MovementIdentity.Persisted)?.id?.let { byTarget[it] }
                        HistoryRow(
                            movement = m,
                            unit = unit,
                            displayQty = correction?.let { it.quantity() } ?: m.quantity(),
                            reason = correction?.let { it.reason() },
                            relTime = relTimeOf(m.occurredAt, now),
                            last = i == rows.lastIndex,
                            tokens = tokens,
                            onCorrect = { correcting = m },
                        )
                    }
                }
                CorrectionSheet(
                    target = correcting,
                    unit = unit,
                    onClose = { correcting = null },
                    onCorrect = onCorrect,
                )
            }
        }
    }
}

@Composable
private fun relTimeLabel(rel: RelTime): String =
    when (rel) {
        RelTime.JustNow -> stringResource(Res.string.history_time_now)
        is RelTime.HoursAgo -> stringResource(Res.string.history_time_hours, rel.hours)
        is RelTime.DaysAgo -> stringResource(Res.string.history_time_days, rel.days)
        is RelTime.OnDate -> stringResource(Res.string.history_time_date, rel.month, rel.day)
    }

@Composable
private fun HistoryRow(
    movement: StockMovement,
    unit: String,
    displayQty: Int,
    reason: String?,
    relTime: RelTime,
    last: Boolean,
    tokens: MindstockTokens,
    onCorrect: () -> Unit,
) {
    val isReplenish = movement is StockMovement.Replenishment
    val label =
        if (isReplenish) stringResource(Res.string.history_replenish) else stringResource(Res.string.history_consume)
    val corrected = reason != null
    val name = movement.actor.profile.displayName()
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // ノード + 縦コネクタ
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(36.dp)) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isReplenish) tokens.accentSoft else tokens.surface2)
                        .border(1.dp, tokens.lineSoft, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(
                    if (isReplenish) AppIconName.Plus else AppIconName.Minus,
                    contentDescription = null,
                    tint = if (isReplenish) tokens.accent else tokens.sub,
                    size = 18.dp,
                )
            }
            if (!last) Box(Modifier.width(2.dp).weight(1f).background(tokens.lineSoft))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f).padding(bottom = if (last) 0.dp else 18.dp)) {
            // ラベル + 数量 + 訂正済バッジ … 右端に相対時刻
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText(
                    "$label $displayQty$unit",
                    style = MindstockType.cardTitle().copy(fontSize = 14.5f.sp),
                    color = tokens.ink,
                )
                if (corrected) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(tokens.accentSoft)
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                    ) {
                        AppText(
                            stringResource(Res.string.history_corrected_badge),
                            style = MindstockType.statusLabel().copy(fontSize = 10.5f.sp),
                            color = tokens.accent,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                AppText(
                    relTimeLabel(relTime),
                    style = MindstockType.unitCaption().copy(fontSize = 12.sp),
                    color = tokens.faint,
                )
            }
            Spacer(Modifier.height(7.dp))
            // 実行者 + メモ … 右端に訂正リンク
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(18.dp).clip(CircleShape).background(tokens.accent),
                    contentAlignment = Alignment.Center,
                ) { AppText(name.take(1), style = MindstockType.statusLabel().copy(fontSize = 9.sp), color = tokens.onAccent) }
                Spacer(Modifier.width(8.dp))
                AppText(
                    if (movement.note().isNotEmpty()) "$name ・ ${movement.note()}" else name,
                    style = MindstockType.unitCaption().copy(fontSize = 12.sp),
                    color = tokens.faint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                AppText(
                    stringResource(Res.string.action_correct),
                    style = MindstockType.statusLabel().copy(fontSize = 12.sp),
                    color = tokens.accent,
                    modifier =
                        Modifier
                            .padding(start = 8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onCorrect)
                            .padding(4.dp),
                )
            }
            // 訂正理由(訂正済のときのみ)
            if (reason != null) {
                Spacer(Modifier.height(5.dp))
                AppText(
                    stringResource(Res.string.history_reason_label, reason),
                    style = MindstockType.unitCaption().copy(fontSize = 11.5f.sp),
                    color = tokens.sub,
                )
            }
        }
    }
}

@Composable
private fun CorrectionSheet(
    target: StockMovement?,
    unit: String,
    onClose: () -> Unit,
    onCorrect: (target: MovementId, quantity: Int, reason: String) -> Unit,
) {
    if (target == null) return
    val movementId = (target.identity as? MovementIdentity.Persisted)?.id
    var qty by remember(target) { mutableStateOf(target.quantity()) }
    var reason by remember(target) { mutableStateOf("") }
    Sheet(open = true, title = stringResource(Res.string.correct_title), onClose = onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Stepper(value = qty, onChange = { qty = it }, unit = unit)
            TextInput(
                value = reason,
                onValueChange = { reason = it },
                placeholder = stringResource(Res.string.correct_reason_placeholder),
                modifier = Modifier.fillMaxWidth(),
                isError = reason.isBlank(),
            )
            PrimaryButton(
                onClick = {
                    if (movementId != null && reason.isNotBlank()) {
                        onCorrect(movementId, qty, reason)
                        onClose()
                    }
                },
                enabled = movementId != null && reason.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { AppText(stringResource(Res.string.correct_submit)) }
        }
    }
}
