package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType

enum class ButtonVariant { Primary, Soft, Ghost, Quiet, Danger }

enum class ButtonSize(
    val height: Int,
    val radius: Int,
) {
    Sm(38, 12),
    Md(50, 15),
    Lg(56, 17),
}

/** 既存呼び出し互換の primary ボタン(中身は AppButton)。 */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = AppButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)

@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    size: ButtonSize = ButtonSize.Md,
    icon: AppIconName? = null,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val tokens = LocalMindstockTokens.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.965f else 1f, label = "btnScale")

    val container: Color
    val contentColor: Color
    val border: BorderStroke?
    when (variant) {
        ButtonVariant.Primary -> {
            container = scheme.primary
            contentColor = scheme.onPrimary
            border = null
        }

        ButtonVariant.Soft -> {
            container = scheme.primaryContainer
            contentColor = scheme.primary
            border = null
        }

        ButtonVariant.Ghost -> {
            container = scheme.surface
            contentColor = scheme.onSurface
            border = BorderStroke(1.dp, scheme.outline)
        }

        ButtonVariant.Quiet -> {
            container = scheme.surfaceVariant
            contentColor = scheme.onSurfaceVariant
            border = null
        }

        ButtonVariant.Danger -> {
            container = tokens.statusOutSoft
            contentColor = tokens.statusOut
            border = null
        }
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = RoundedCornerShape(size.radius.dp),
        border = border,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = 18.dp),
        modifier = modifier.height(size.height.dp).scale(scale),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) AppIcon(icon, contentDescription = null)
            CompositionLocalProvider(LocalTextStyle provides MindstockType.button()) { content() }
        }
    }
}
