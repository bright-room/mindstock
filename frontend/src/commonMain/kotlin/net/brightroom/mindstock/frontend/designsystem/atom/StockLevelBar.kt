package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.max

@Composable
fun StockLevelBar(
    qty: Int,
    min: Int,
    color: Color,
    modifier: Modifier = Modifier,
    trackColor: Color = color.copy(alpha = 0.16f),
) {
    val comfortable = max(max(min * 2, min + 3), max(qty, 1))
    val pct = (qty.toFloat() / comfortable).coerceIn(0f, 1f)
    LinearProgressIndicator(
        progress = { pct },
        color = color,
        trackColor = trackColor,
        modifier = modifier.fillMaxWidth().height(8.dp),
    )
}
