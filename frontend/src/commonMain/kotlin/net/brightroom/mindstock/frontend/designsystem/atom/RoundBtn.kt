package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import net.brightroom.mindstock.frontend.designsystem.theme.ShadowLevel
import net.brightroom.mindstock.frontend.designsystem.theme.softShadow

/** 円形アイコンボタン（戻る/設定 等の chrome 用）。 */
@Composable
fun RoundBtn(
    icon: AppIconName,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    IconButton(
        onClick = onClick,
        modifier =
            modifier
                .size(58.dp)
                .softShadow(ShadowLevel.Sm, CircleShape)
                .clip(CircleShape)
                .background(scheme.surface)
                .border(BorderStroke(1.dp, scheme.outline), CircleShape),
    ) {
        AppIcon(icon, contentDescription = contentDescription, size = 24.dp, tint = scheme.onSurface)
    }
}
