package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType

/** 一覧の空状態。アイコン + タイトル + 補足。 */
@Composable
fun EmptyState(
    icon: AppIconName,
    title: String,
    sub: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val tokens = LocalMindstockTokens.current
    Column(
        modifier = modifier.fillMaxWidth().padding(PaddingValues(vertical = 48.dp, horizontal = 24.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, contentDescription = null, size = 34.dp, tint = tokens.faint)
        }
        AppText(title, style = MindstockType.summaryTitle(), color = scheme.onSurface, textAlign = TextAlign.Center)
        AppText(sub, style = MindstockType.sectionMeta(), color = tokens.faint, textAlign = TextAlign.Center)
    }
}
