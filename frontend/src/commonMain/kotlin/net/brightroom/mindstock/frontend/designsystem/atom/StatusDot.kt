package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType

/** ok/low/out のドット。color/soft は呼び出し側が MindstockTokens から渡す。 */
@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    soft: Color = Color.Unspecified,
    label: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Canvas(modifier = Modifier.size(14.dp)) {
            if (soft != Color.Unspecified) drawCircle(color = soft, radius = 7.dp.toPx(), center = center)
            drawCircle(color = color, radius = 4.dp.toPx(), center = center)
        }
        if (label != null) {
            Spacer(Modifier.size(6.dp))
            AppText(text = label, style = MindstockType.statusLabel(), color = color)
        }
    }
}
