package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 数量 ± ステッパ。min 1 でクランプ（補充/消費/訂正の数量入力）。 */
@Composable
fun Stepper(
    value: Int,
    onChange: (Int) -> Unit,
    unit: String,
    modifier: Modifier = Modifier,
    min: Int = 1,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundBtn(AppIconName.Minus, contentDescription = "decrement", onClick = { onChange((value - 1).coerceAtLeast(min)) })
        Text(
            text = "$value$unit",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        RoundBtn(AppIconName.Plus, contentDescription = "increment", onClick = { onChange(value + 1) })
    }
}
