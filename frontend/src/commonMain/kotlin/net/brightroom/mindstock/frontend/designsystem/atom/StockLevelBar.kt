package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens

@Composable
fun StockLevelBar(
    qty: Int,
    min: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMindstockTokens.current
    val track = MaterialTheme.colorScheme.surfaceVariant
    val target = fillFraction(qty, min)
    val minPos = minFraction(qty, min)
    val animated by animateFloatAsState(target, label = "stockFill")
    Canvas(modifier = modifier.fillMaxWidth().height(8.dp)) {
        val h = size.height
        val r = CornerRadius(h / 2, h / 2)
        drawRoundRect(color = track, size = Size(size.width, h), cornerRadius = r)
        if (animated > 0f) {
            drawRoundRect(color = color, size = Size(size.width * animated, h), cornerRadius = r)
        }
        val x = size.width * minPos
        drawRoundRect(
            color = tokens.faint.copy(alpha = 0.5f),
            topLeft = Offset(x - 1.dp.toPx(), -3.dp.toPx()),
            size = Size(2.dp.toPx(), h + 6.dp.toPx()),
            cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
        )
    }
}
