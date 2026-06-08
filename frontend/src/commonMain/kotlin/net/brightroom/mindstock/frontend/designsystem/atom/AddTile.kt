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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType

/**
 * 破線の「追加」タイル。
 * 既定はニュートラル(line + sub)。[accent]=true で mock の商品マスタ用(accent 破線 + accent `700 14.5px`)。
 */
@Composable
fun AddTile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val dash = if (accent) scheme.primary else scheme.outline
    val content = if (accent) scheme.primary else scheme.onSurfaceVariant
    val style =
        if (accent) {
            MindstockType.sectionMeta().copy(
                fontWeight = FontWeight.Bold,
                fontSize = 14.5f.sp,
            )
        } else {
            MindstockType.sectionMeta()
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(if (accent) 54.dp else 58.dp)
                .drawBehind {
                    drawRoundRect(
                        color = dash,
                        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                        cornerRadius = CornerRadius(22.dp.toPx()),
                    )
                }.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        AppIcon(AppIconName.Plus, contentDescription = null, size = 18.dp, tint = content)
        AppText(label, style = style, color = content)
    }
}
