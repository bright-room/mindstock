package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 商品サムネ。ProductImage 表示は将来（P6-2）。現状は icon プレースホルダ。 */
@Composable
fun Thumb(
    size: Dp = 48.dp,
    radius: Dp = 14.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(RoundedCornerShape(radius))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(AppIconName.Box, contentDescription = null)
    }
}
