package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType

/**
 * 数量 ± ステッパ。min 1 でクランプ（補充/消費/訂正の数量入力）。
 * モック core.jsx の `Stepper`: 両端に円形 ± / 中央は大数字 `700 52px/1` tnum + 単位 `500 13px/1` を**下に**。
 */
@Composable
fun Stepper(
    value: Int,
    onChange: (Int) -> Unit,
    unit: String,
    modifier: Modifier = Modifier,
    min: Int = 1,
) {
    val tokens = LocalMindstockTokens.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundBtn(AppIconName.Minus, contentDescription = "decrement", onClick = { onChange((value - 1).coerceAtLeast(min)) })
        Column(
            modifier = Modifier.widthIn(min = 92.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppText(
                text = "$value",
                style = MindstockType.bigQty().copy(fontSize = 52.sp, lineHeight = 52.sp),
                color = tokens.ink,
            )
            if (unit.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                AppText(unit, style = MindstockType.greeting(), color = tokens.faint)
            }
        }
        RoundBtn(AppIconName.Plus, contentDescription = "increment", onClick = { onChange(value + 1) })
    }
}
