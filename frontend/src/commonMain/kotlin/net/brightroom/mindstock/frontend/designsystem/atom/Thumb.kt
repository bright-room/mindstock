package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 商品サムネ。画像表示は将来(P6-2)。現状はハッチ背景 + カテゴリアイコン。 */
@Composable
fun Thumb(
    icon: AppIconName = AppIconName.Box,
    size: Dp = 48.dp,
    radius: Dp = 14.dp,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val hatch = scheme.primary.copy(alpha = 0.08f)
    Box(
        modifier =
            modifier
                .size(size)
                .clip(RoundedCornerShape(radius))
                .border(BorderStroke(1.dp, scheme.outlineVariant), RoundedCornerShape(radius))
                .drawBehind {
                    drawRect(scheme.surfaceVariant)
                    val step = 10.dp.toPx()
                    val w = this.size.width
                    val h = this.size.height
                    var x = -h
                    while (x < w) {
                        drawLine(
                            color = hatch,
                            start = Offset(x, h),
                            end = Offset(x + h, 0f),
                            strokeWidth = 5.dp.toPx(),
                        )
                        x += step
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(icon, contentDescription = null, size = size * 0.5f, tint = scheme.primary)
    }
}
