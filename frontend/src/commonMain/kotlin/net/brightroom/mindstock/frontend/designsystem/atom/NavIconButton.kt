package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.ShadowLevel
import net.brightroom.mindstock.frontend.designsystem.theme.softShadow

/**
 * 角丸スクエアのアイコンボタン(戻る/設定/お知らせ 等の chrome 用)。
 * モック `app.jsx` の `navBtn`(44x44・radius 13・border・surface・shadow.sm)準拠。
 * 円形の [RoundBtn](Stepper の +/- 用)とは別物。
 *
 * [badge] = true で右上に赤い通知ドット(mock のベル: 8px・status.out・surface 2px リング)を出す。
 */
@Composable
fun NavIconButton(
    icon: AppIconName,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val tokens = LocalMindstockTokens.current
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier =
            modifier
                .size(44.dp)
                .softShadow(ShadowLevel.Sm, shape)
                .clip(shape)
                .background(scheme.surface)
                .border(1.dp, scheme.outline, shape)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(icon, contentDescription = contentDescription, size = 20.dp, tint = scheme.onSurface)
        if (badge) {
            // 赤ドット + surface リング(mock 準拠)。
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 7.dp, end = 8.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(tokens.surface),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(tokens.statusOut))
            }
        }
    }
}
