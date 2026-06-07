package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType

/** 候補文字列のチップ行。タップで onPick に値を渡す。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SuggestionChips(
    suggestions: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMindstockTokens.current
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        suggestions.forEach { s ->
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(tokens.surface)
                        .border(BorderStroke(1.dp, tokens.line), RoundedCornerShape(99.dp))
                        .clickable { onPick(s) }
                        .padding(horizontal = 13.dp, vertical = 9.dp),
            ) {
                AppText(s, style = MindstockType.sectionMeta(), color = tokens.sub)
            }
        }
    }
}
