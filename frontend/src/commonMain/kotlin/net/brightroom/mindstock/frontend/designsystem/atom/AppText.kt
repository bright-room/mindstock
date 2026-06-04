package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** feature 層が material3.Text を直接 import しないための最小ラッパ。詳細な typography 適用は今後。 */
@Composable
fun AppText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text = text, modifier = modifier)
}
