package net.brightroom.mindstock.frontend.feature.inventory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.LocalDateTime
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.move_after_negative
import mindstock.frontend.generated.resources.move_current_short
import mindstock.frontend.generated.resources.move_note_consume_placeholder
import mindstock.frontend.generated.resources.move_note_placeholder
import mindstock.frontend.generated.resources.move_submit
import mindstock.frontend.generated.resources.move_title_consume
import mindstock.frontend.generated.resources.move_title_replenish
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt
import net.brightroom.mindstock.extensions.kotlinx.datetime.now
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.DatePick
import net.brightroom.mindstock.frontend.designsystem.atom.PrimaryButton
import net.brightroom.mindstock.frontend.designsystem.atom.Sheet
import net.brightroom.mindstock.frontend.designsystem.atom.Stepper
import net.brightroom.mindstock.frontend.designsystem.atom.TextInput
import net.brightroom.mindstock.frontend.designsystem.atom.Thumb
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import net.brightroom.mindstock.frontend.feature.inventory.glyphForProductName
import org.jetbrains.compose.resources.stringResource

enum class MoveMode { Replenish, Consume }

/**
 * 補充/消費シート。商品サマリ + 数量 + 増減プレビュー + 日時ピッカーで occurredAt を指定(バックデート可) + メモ。
 * 買い物リストからの補充など occurredAt を now 固定にしたい呼び出しは [showDatePicker] = false でピッカーを隠す。
 */
@Composable
fun MoveSheet(
    open: Boolean,
    mode: MoveMode,
    stock: Stock?,
    onClose: () -> Unit,
    onSubmit: (quantity: Int, note: String, occurredAt: OccurredAt) -> Unit,
    showDatePicker: Boolean = true,
) {
    if (stock == null) return
    val tokens = LocalMindstockTokens.current
    val isReplenish = mode == MoveMode.Replenish
    val title = stringResource(if (isReplenish) Res.string.move_title_replenish else Res.string.move_title_consume)
    val unit = stock.product.setting.unit()
    val current = stock.currentQuantity()()
    var qty by remember(open, stock) { mutableStateOf(1) }
    var note by remember(open, stock) { mutableStateOf("") }
    val today = remember(open) { LocalDateTime.now().date }
    var pickedDate by remember(open, stock) { mutableStateOf(today) }
    val after = if (isReplenish) current + qty else current - qty
    Sheet(open = open, title = title, onClose = onClose) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            // 商品サマリ
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(tokens.radiusMd))
                        .background(tokens.surface2)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Thumb(icon = glyphForProductName(stock.product.name()), size = 42.dp)
                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        stock.product.name(),
                        style = MindstockType.cardTitle(),
                        color = tokens.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.padding(top = 3.dp))
                    AppText(
                        stringResource(Res.string.move_current_short, current, unit),
                        style = MindstockType.unitCaption().copy(fontSize = 12.5f.sp),
                        color = tokens.faint,
                    )
                }
            }

            Stepper(value = qty, onChange = { qty = it }, unit = unit)

            // 増減プレビュー
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText("$current$unit", style = MindstockType.summarySub().copy(fontSize = 13.5f.sp), color = tokens.sub)
                Spacer(Modifier.width(12.dp))
                AppIcon(AppIconName.ChevronRight, contentDescription = null, tint = tokens.faint, size = 15.dp)
                Spacer(Modifier.width(12.dp))
                AppText(
                    "$after$unit",
                    style = MindstockType.summaryTitle().copy(fontSize = 16.sp),
                    color = if (after < 0) tokens.statusOut else tokens.ink,
                )
                if (after < 0) {
                    Spacer(Modifier.width(8.dp))
                    AppText(
                        stringResource(Res.string.move_after_negative),
                        style = MindstockType.statusLabel().copy(fontSize = 11.5f.sp),
                        color = tokens.statusOut,
                    )
                }
            }

            if (showDatePicker) {
                DatePick(
                    today = today,
                    selected = pickedDate,
                    onSelect = { pickedDate = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            TextInput(
                value = note,
                onValueChange = { note = it },
                placeholder =
                    stringResource(
                        if (isReplenish) Res.string.move_note_placeholder else Res.string.move_note_consume_placeholder,
                    ),
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(
                onClick = {
                    onSubmit(qty, note, occurredAtOf(pickedDate, LocalDateTime.now()))
                    onClose()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                AppText(stringResource(Res.string.move_submit, qty, unit, title))
            }
        }
    }
}
