package net.brightroom.mindstock.frontend.feature.household.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.join_code_busy
import mindstock.frontend.generated.resources.join_code_cta
import mindstock.frontend.generated.resources.join_code_placeholder
import mindstock.frontend.generated.resources.join_code_preview_household
import mindstock.frontend.generated.resources.join_code_preview_role
import mindstock.frontend.generated.resources.join_code_sub
import mindstock.frontend.generated.resources.join_code_title
import net.brightroom.mindstock.frontend.core.ui.resolve
import net.brightroom.mindstock.frontend.designsystem.atom.AppButton
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.ButtonSize
import net.brightroom.mindstock.frontend.designsystem.atom.CodeInput
import net.brightroom.mindstock.frontend.designsystem.atom.Sheet
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import net.brightroom.mindstock.frontend.feature.household.NeedHouseholdUiState
import org.jetbrains.compose.resources.stringResource

@Composable
fun JoinCodeSheet(
    open: Boolean,
    state: NeedHouseholdUiState,
    onClose: () -> Unit,
    onCodeChange: (String) -> Unit,
    onJoin: (String) -> Unit,
) {
    val tokens = LocalMindstockTokens.current
    var code by remember(open) { mutableStateOf("") }
    LaunchedEffect(code) { onCodeChange(code) }
    Sheet(open = open, title = stringResource(Res.string.join_code_title), onClose = onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            AppText(stringResource(Res.string.join_code_sub), style = MindstockType.summarySub(), color = tokens.sub)
            CodeInput(
                value = code,
                onValueChange = { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) },
                placeholder = stringResource(Res.string.join_code_placeholder),
                isError = state.previewError != null && code.length == 6,
                modifier = Modifier.fillMaxWidth(),
            )
            val err = state.previewError
            if (err != null && code.length == 6) {
                AppText(err.resolve(), style = MindstockType.summarySub(), color = tokens.statusOut)
            }
            val preview = state.preview
            if (preview != null) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(tokens.surface)
                            .border(1.dp, tokens.lineSoft, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppText(
                        stringResource(Res.string.join_code_preview_household),
                        style = MindstockType.sectionMeta(),
                        color = tokens.faint,
                    )
                    AppText(preview.householdName(), style = MindstockType.summaryTitle(), color = tokens.ink)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppIcon(AppIconName.User, contentDescription = null, size = 14.dp, tint = tokens.faint)
                        AppText(
                            stringResource(Res.string.join_code_preview_role, stringResource(roleLabelResource(preview.grantedRole))),
                            style = MindstockType.sectionMeta(),
                            color = tokens.sub,
                        )
                    }
                }
            }
            AppButton(
                onClick = { onJoin(code) },
                size = ButtonSize.Lg,
                icon = AppIconName.Link,
                enabled = state.preview != null && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                AppText(stringResource(if (state.busy) Res.string.join_code_busy else Res.string.join_code_cta))
            }
        }
    }
}
