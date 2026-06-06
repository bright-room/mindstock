package net.brightroom.mindstock.frontend.feature.inventory.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.action_consume
import mindstock.frontend.generated.resources.action_replenish
import mindstock.frontend.generated.resources.move_current_qty
import mindstock.frontend.generated.resources.move_note_placeholder
import mindstock.frontend.generated.resources.move_submit
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.PrimaryButton
import net.brightroom.mindstock.frontend.designsystem.atom.Sheet
import net.brightroom.mindstock.frontend.designsystem.atom.Stepper
import net.brightroom.mindstock.frontend.designsystem.atom.TextInput
import org.jetbrains.compose.resources.stringResource

enum class MoveMode { Replenish, Consume }

/** 補充/消費シート。数量+メモ（日時ピッカーは無し＝サーバ時刻確定）。 */
@Composable
fun MoveSheet(
    open: Boolean,
    mode: MoveMode,
    stock: Stock?,
    onClose: () -> Unit,
    onSubmit: (quantity: Int, note: String) -> Unit,
) {
    if (stock == null) return
    val isReplenish = mode == MoveMode.Replenish
    val title = stringResource(if (isReplenish) Res.string.action_replenish else Res.string.action_consume)
    var qty by remember(open, stock) { mutableStateOf(1) }
    var note by remember(open, stock) { mutableStateOf("") }
    Sheet(open = open, title = title, onClose = onClose) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            AppText(
                stringResource(Res.string.move_current_qty, stock.product.name(), stock.currentQuantity(), stock.product.setting.unit()),
            )
            Stepper(value = qty, onChange = { qty = it }, unit = stock.product.setting.unit())
            TextInput(
                value = note,
                onValueChange = { note = it },
                placeholder = stringResource(Res.string.move_note_placeholder),
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(onClick = {
                onSubmit(qty, note)
                onClose()
            }) {
                AppText(stringResource(Res.string.move_submit, qty, stock.product.setting.unit(), title))
            }
        }
    }
}
