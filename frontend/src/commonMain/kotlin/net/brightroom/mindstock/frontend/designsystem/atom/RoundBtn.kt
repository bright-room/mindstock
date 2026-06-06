package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** 円形アイコンボタン（戻る/設定 等の chrome 用）。 */
@Composable
fun RoundBtn(
    icon: AppIconName,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalIconButton(onClick = onClick, shape = CircleShape, modifier = modifier) {
        AppIcon(icon, contentDescription = contentDescription)
    }
}
