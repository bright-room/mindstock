package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType

/** 小見出し(セクションラベル)。スタイルは画面ごとに差があるため [style] でパラメータ化(既定 sectionMeta)。色は faint。 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MindstockType.sectionMeta(),
) {
    val tokens = LocalMindstockTokens.current
    AppText(text, style = style, color = tokens.faint, modifier = modifier)
}
