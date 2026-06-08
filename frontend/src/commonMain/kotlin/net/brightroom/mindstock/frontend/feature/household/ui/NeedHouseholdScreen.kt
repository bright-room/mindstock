package net.brightroom.mindstock.frontend.feature.household.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.need_household_create
import mindstock.frontend.generated.resources.need_household_join
import mindstock.frontend.generated.resources.need_household_sub
import mindstock.frontend.generated.resources.need_household_title
import net.brightroom.mindstock.frontend.designsystem.atom.AppButton
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.ButtonSize
import net.brightroom.mindstock.frontend.designsystem.atom.ButtonVariant
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import org.jetbrains.compose.resources.stringResource

@Composable
fun NeedHouseholdScreen(
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMindstockTokens.current
    Box(modifier = modifier.fillMaxSize().background(tokens.surface2).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(tokens.radiusLg))
                    .background(tokens.surface)
                    .border(1.dp, tokens.lineSoft, RoundedCornerShape(tokens.radiusLg))
                    .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(tokens.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(AppIconName.Home, contentDescription = null, size = 26.dp, tint = tokens.accent)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppText(stringResource(Res.string.need_household_title), style = MindstockType.summaryTitle(), color = tokens.ink)
                AppText(stringResource(Res.string.need_household_sub), style = MindstockType.summarySub(), color = tokens.sub)
            }
            AppButton(onClick = onCreate, size = ButtonSize.Lg, icon = AppIconName.Home, modifier = Modifier.fillMaxWidth()) {
                AppText(stringResource(Res.string.need_household_create))
            }
            AppButton(
                onClick = onJoin,
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Lg,
                icon = AppIconName.Link,
                modifier = Modifier.fillMaxWidth(),
            ) {
                AppText(stringResource(Res.string.need_household_join))
            }
        }
    }
}
