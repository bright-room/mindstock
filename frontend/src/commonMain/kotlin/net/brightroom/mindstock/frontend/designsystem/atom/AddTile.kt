package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType

@Composable
fun AddTile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(58.dp)
                .drawBehind {
                    drawRoundRect(
                        color = scheme.outline,
                        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                        cornerRadius = CornerRadius(22.dp.toPx()),
                    )
                }.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        AppIcon(AppIconName.Plus, contentDescription = null, size = 18.dp, tint = scheme.onSurfaceVariant)
        AppText(label, style = MindstockType.sectionMeta(), color = scheme.onSurfaceVariant)
    }
}
