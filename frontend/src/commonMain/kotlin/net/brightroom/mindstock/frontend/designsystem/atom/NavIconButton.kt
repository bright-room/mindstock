package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import net.brightroom.mindstock.frontend.designsystem.theme.ShadowLevel
import net.brightroom.mindstock.frontend.designsystem.theme.softShadow

/**
 * 角丸スクエアのアイコンボタン(戻る/設定/お知らせ 等の chrome 用)。
 * モック `app.jsx` の `navBtn`(42x42・radius 13・border・surface・shadow.sm)準拠。
 * 円形の [RoundBtn](Stepper の +/- 用)とは別物。
 */
@Composable
fun NavIconButton(
    icon: AppIconName,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(13.dp)
    IconButton(
        onClick = onClick,
        modifier =
            modifier
                .size(44.dp)
                .softShadow(ShadowLevel.Sm, shape)
                .clip(shape)
                .background(scheme.surface)
                .border(BorderStroke(1.dp, scheme.outline), shape),
    ) {
        AppIcon(icon, contentDescription = contentDescription, size = 20.dp, tint = scheme.onSurface)
    }
}
