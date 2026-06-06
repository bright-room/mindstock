package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val tokens = LocalMindstockTokens.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val active = focused || value.isNotEmpty()
    Row(
        modifier =
            modifier
                .height(50.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(scheme.surface)
                .border(1.dp, if (active) scheme.primary else scheme.outline, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppIcon(AppIconName.Search, contentDescription = null, size = 19.dp, tint = if (active) scheme.primary else tokens.faint)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            interactionSource = interaction,
            singleLine = true,
            textStyle = MindstockType.button().copy(color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.primary),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (value.isEmpty()) AppText(placeholder, style = MindstockType.button(), color = tokens.faint)
                inner()
            },
        )
        if (value.isNotEmpty()) {
            Box(
                modifier =
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(scheme.surfaceVariant)
                        .clickable { onValueChange("") },
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(AppIconName.Close, contentDescription = "clear", size = 16.dp, tint = tokens.sub)
            }
        }
    }
}
