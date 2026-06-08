package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.ShadowLevel
import net.brightroom.mindstock.frontend.designsystem.theme.softShadow

/**
 * 招待コードの入力欄。mock `screens-household.jsx:JoinCodeSheet` の input 準拠:
 * height64 / radius16 / 1.5px border(入力済で accent)/ surface / shadow.sm /
 * 中央寄せ・monospace `700 26px`・letterSpacing 0.22em。大文字化/桁制限は callsite 側で行う。
 */
@Composable
fun CodeInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    val tokens = LocalMindstockTokens.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val active = focused || value.isNotEmpty()
    val border =
        when {
            isError -> tokens.statusOut
            active -> tokens.accent
            else -> tokens.line
        }
    val codeStyle =
        TextStyle(
            color = tokens.ink,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            lineHeight = 26.sp,
            letterSpacing = 0.22.em,
            textAlign = TextAlign.Center,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
        )
    Box(
        modifier =
            modifier
                .height(64.dp)
                .softShadow(ShadowLevel.Sm, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(tokens.surface)
                .border(1.5.dp, border, RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            interactionSource = interaction,
            singleLine = true,
            textStyle = codeStyle,
            cursorBrush = SolidColor(tokens.accent),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    AppText(
                        placeholder,
                        style = codeStyle.copy(color = tokens.faint),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                inner()
            },
        )
    }
}
