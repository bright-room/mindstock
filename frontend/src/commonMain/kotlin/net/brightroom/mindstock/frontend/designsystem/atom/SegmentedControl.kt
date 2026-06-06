package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import net.brightroom.mindstock.frontend.designsystem.theme.ShadowLevel
import net.brightroom.mindstock.frontend.designsystem.theme.softShadow

data class SegOption(
    val key: String,
    val label: String,
)

@Composable
fun SegmentedControl(
    options: List<SegOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val tokens = LocalMindstockTokens.current
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(13.dp))
                .background(scheme.surfaceVariant)
                .padding(3.dp),
    ) {
        options.forEach { o ->
            val active = o.key == selectedKey
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(34.dp)
                        .then(if (active) Modifier.softShadow(ShadowLevel.Sm, RoundedCornerShape(10.dp)) else Modifier)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) scheme.surface else Color.Transparent)
                        .clickable { onSelect(o.key) },
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    text = o.label,
                    style = MindstockType.sectionMeta(),
                    color = if (active) scheme.onSurface else tokens.faint,
                )
            }
        }
    }
}
