package net.brightroom.mindstock.frontend.feature.household.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.create_household_busy
import mindstock.frontend.generated.resources.create_household_cta
import mindstock.frontend.generated.resources.create_household_placeholder
import mindstock.frontend.generated.resources.create_household_sub
import mindstock.frontend.generated.resources.create_household_title
import mindstock.frontend.generated.resources.create_suggest_1
import mindstock.frontend.generated.resources.create_suggest_2
import mindstock.frontend.generated.resources.create_suggest_3
import mindstock.frontend.generated.resources.create_suggest_4
import net.brightroom.mindstock.frontend.designsystem.atom.AppButton
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.ButtonSize
import net.brightroom.mindstock.frontend.designsystem.atom.Sheet
import net.brightroom.mindstock.frontend.designsystem.atom.SuggestionChips
import net.brightroom.mindstock.frontend.designsystem.atom.TextInput
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import org.jetbrains.compose.resources.stringResource

@Composable
fun CreateHouseholdSheet(
    open: Boolean,
    busy: Boolean,
    onClose: () -> Unit,
    onCreate: (String) -> Unit,
) {
    val tokens = LocalMindstockTokens.current
    var name by remember(open) { mutableStateOf("") }
    Sheet(open = open, title = stringResource(Res.string.create_household_title), onClose = onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            AppText(stringResource(Res.string.create_household_sub), style = MindstockType.summarySub(), color = tokens.sub)
            TextInput(
                value = name,
                onValueChange = { name = it },
                placeholder = stringResource(Res.string.create_household_placeholder),
                modifier = Modifier.fillMaxWidth(),
            )
            SuggestionChips(
                suggestions =
                    listOf(
                        stringResource(Res.string.create_suggest_1),
                        stringResource(Res.string.create_suggest_2),
                        stringResource(Res.string.create_suggest_3),
                        stringResource(Res.string.create_suggest_4),
                    ),
                onPick = { name = it },
            )
            AppButton(
                onClick = { onCreate(name) },
                size = ButtonSize.Lg,
                icon = AppIconName.Home,
                enabled = name.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                AppText(stringResource(if (busy) Res.string.create_household_busy else Res.string.create_household_cta))
            }
        }
    }
}
