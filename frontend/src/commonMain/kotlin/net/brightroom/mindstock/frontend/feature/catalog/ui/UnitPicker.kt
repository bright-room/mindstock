package net.brightroom.mindstock.frontend.feature.catalog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.add_product_unit_other
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.TextInput
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import org.jetbrains.compose.resources.stringResource

private val COMMON_UNITS = listOf("個", "本", "袋", "パック", "箱", "ロール", "缶", "枚", "セット")

/** 単位選択。共通チップ + 自由入力。value はトリム前の生文字列。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnitPicker(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMindstockTokens.current
    val custom = value.isNotEmpty() && value !in COMMON_UNITS
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            COMMON_UNITS.forEach { u ->
                val active = value == u
                AppText(
                    text = u,
                    style = MindstockType.button(),
                    color = if (active) tokens.accent else tokens.sub,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .border(1.5.dp, if (active) tokens.accent else tokens.line, RoundedCornerShape(99.dp))
                            .background(if (active) tokens.accentSoft else tokens.surface)
                            .clickable { onChange(u) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        TextInput(
            value = if (custom) value else "",
            onValueChange = onChange,
            placeholder = stringResource(Res.string.add_product_unit_other),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
