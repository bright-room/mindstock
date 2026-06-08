package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType

/**
 * 小型の ± ステッパ（最低在庫の調整など）。
 * モック core.jsx の `miniStep`: 40x40・radius11・border・surface の角丸スクエア ± + `700 22px/1` tnum 数字。
 * 大きな円形 [Stepper]（補充/消費の数量）とは別物。
 */
@Composable
fun MiniStepper(
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 0,
) {
    val tokens = LocalMindstockTokens.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniStepButton(AppIconName.Minus, "decrement") { onChange((value - 1).coerceAtLeast(min)) }
        AppText(
            "$value",
            style = MindstockType.bigQty().copy(fontSize = 22.sp, lineHeight = 22.sp),
            color = tokens.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 26.dp),
        )
        MiniStepButton(AppIconName.Plus, "increment") { onChange(value + 1) }
    }
}

@Composable
private fun MiniStepButton(
    icon: AppIconName,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val tokens = LocalMindstockTokens.current
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(shape)
                .background(tokens.surface)
                .border(1.dp, tokens.line, shape)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(icon, contentDescription = contentDescription, size = 18.dp, tint = tokens.ink)
    }
}
