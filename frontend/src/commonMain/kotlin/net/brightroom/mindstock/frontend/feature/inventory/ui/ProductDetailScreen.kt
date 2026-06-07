package net.brightroom.mindstock.frontend.feature.inventory.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import mindstock.frontend.generated.resources.history_replenish
import mindstock.frontend.generated.resources.loading
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.StockStatus
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementIdentity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement
import net.brightroom.mindstock.frontend.core.ui.resolve
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.PrimaryButton
import net.brightroom.mindstock.frontend.designsystem.atom.RoundBtn
import net.brightroom.mindstock.frontend.designsystem.atom.Sheet
import net.brightroom.mindstock.frontend.designsystem.atom.StatusDot
import net.brightroom.mindstock.frontend.designsystem.atom.Stepper
import net.brightroom.mindstock.frontend.designsystem.atom.StockLevelBar
import net.brightroom.mindstock.frontend.designsystem.atom.TextInput
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.feature.inventory.ProductDetailUiState
import org.jetbrains.compose.resources.stringResource

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
    // ヘッダの Stock: Content があればそれ、無ければ seed
    val stock: Stock? = (detail as? ProductDetailUiState.Content)?.stock ?: seed
    val wanted: Boolean? = (detail as? ProductDetailUiState.Content)?.wanted

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (stock == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundBtn(AppIconName.Back, contentDescription = "back", onClick = onBack)
            }
            if (detail is ProductDetailUiState.Error) AppText(detail.text.resolve()) else AppText(stringResource(Res.string.loading))
            return@Column
        }

        val tokens = LocalMindstockTokens.current
        val statusColor =
            when (stock.status()) {
                StockStatus.在庫切れ -> tokens.statusOut
                StockStatus.残りわずか -> tokens.statusLow
                StockStatus.十分 -> tokens.statusOk
            }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundBtn(AppIconName.Back, contentDescription = stringResource(Res.string.action_back), onClick = onBack)
            Spacer(Modifier.width(8.dp))
            AppText(stock.product.name())
            Spacer(Modifier.weight(1f))
            if (onOpenSettings != null) {
                RoundBtn(AppIconName.Settings, contentDescription = stringResource(Res.string.action_settings), onClick = onOpenSettings)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(color = statusColor)
            AppText("${stock.currentQuantity()}${stock.product.setting.unit()}")
        }
        StockLevelBar(qty = stock.currentQuantity(), min = stock.product.setting.minimumStock(), color = statusColor)
        AppText(
            stringResource(
                Res.string.detail_min_stock,
                stock.product.setting.minimumStock(),
                stock.product.setting.unit(),
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(onClick = onReplenish) { AppText(stringResource(Res.string.action_replenish)) }
            PrimaryButton(onClick = onConsume) { AppText(stringResource(Res.string.action_consume)) }
        }

        // wanted トグル領域（status==十分 のときのみ操作可能。それ以外は自動表示の案内）
        when {
            stock.status() != StockStatus.十分 -> {
                AppText(stringResource(Res.string.detail_wanted_auto))
            }

            wanted == true -> {
                PrimaryButton(
                    onClick = { onToggleWanted(false) },
                ) { AppText(stringResource(Res.string.detail_wanted_remove)) }
            }

            wanted == false -> {
                PrimaryButton(onClick = { onToggleWanted(true) }) { AppText(stringResource(Res.string.detail_wanted_add)) }
            }

            else -> {
                Unit
            } // wanted 未確定（ロード中）はトグル非表示
        }

        AppText(stringResource(Res.string.detail_history))
        when (detail) {
            is ProductDetailUiState.Loading -> {
                AppText(stringResource(Res.string.loading))
            }

            is ProductDetailUiState.Error -> {
                AppText(detail.text.resolve())
            }

            is ProductDetailUiState.Content -> {
                val correctedIds =
                    detail.movements.list
                        .filterIsInstance<StockMovement.Correction>()
                        .map { it.target }
                        .toSet()
                if (detail.movements.list.isEmpty()) {
                    AppText(stringResource(Res.string.detail_history_empty))
                } else {
                    var correcting by remember { mutableStateOf<StockMovement?>(null) }
                    LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(detail.movements.list.reversed()) { m ->
                            val corrected = (m.identity as? MovementIdentity.Persisted)?.id in correctedIds
                            HistoryRow(m, stock.product.setting.unit(), corrected = corrected, onCorrect = { correcting = m })
                        }
                    }
                    CorrectionSheet(
                        target = correcting,
                        unit = stock.product.setting.unit(),
                        onClose = { correcting = null },
                        onCorrect = onCorrect,
                    )
                }
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
            ) { AppText(stringResource(Res.string.correct_submit)) }
        }
    }
}

@Composable
private fun HistoryRow(
    movement: StockMovement,
    unit: String,
    corrected: Boolean,
    onCorrect: () -> Unit,
) {
    val label =
        when (movement) {
            is StockMovement.Replenishment -> stringResource(Res.string.history_replenish)
            is StockMovement.Consumption -> stringResource(Res.string.history_consume)
            is StockMovement.Correction -> stringResource(Res.string.action_correct)
        }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText("$label ${movement.quantity()}$unit")
        AppText(movement.actor.profile.displayName())
        if (movement.note().isNotEmpty()) AppText(movement.note())
        if (corrected) AppText(stringResource(Res.string.history_corrected_badge))
        if (movement is StockMovement.Replenishment || movement is StockMovement.Consumption) {
            PrimaryButton(onClick = onCorrect) { AppText(stringResource(Res.string.action_correct)) }
        }
    }
}
