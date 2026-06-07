package net.brightroom.mindstock.frontend.feature.household.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.settings_household_member_count
import mindstock.frontend.generated.resources.switcher_create
import mindstock.frontend.generated.resources.switcher_create_sub
import mindstock.frontend.generated.resources.switcher_desc
import mindstock.frontend.generated.resources.switcher_join
import mindstock.frontend.generated.resources.switcher_join_sub
import mindstock.frontend.generated.resources.switcher_title
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.Sheet
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import net.brightroom.mindstock.frontend.feature.household.HouseholdSummary
import org.jetbrains.compose.resources.stringResource

private const val SEPARATOR = "·"

@Composable
fun HouseholdSwitcher(
    open: Boolean,
    households: List<HouseholdSummary>,
    onClose: () -> Unit,
    onChoose: (HouseholdId) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
) {
    val tokens = LocalMindstockTokens.current
    Sheet(open = open, title = stringResource(Res.string.switcher_title), onClose = onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            AppText(
                stringResource(Res.string.switcher_desc),
                style = MindstockType.summarySub(),
                color = tokens.sub,
                modifier = Modifier.padding(bottom = 7.dp),
            )
            households.forEach { h ->
                HouseholdRow(summary = h, onClick = { onChoose(h.id) })
            }
            SwitcherAction(
                icon = AppIconName.Plus,
                label = stringResource(Res.string.switcher_create),
                sub = stringResource(Res.string.switcher_create_sub),
                accent = true,
                onClick = onCreate,
                modifier = Modifier.padding(top = 7.dp),
            )
            SwitcherAction(
                icon = AppIconName.Link,
                label = stringResource(Res.string.switcher_join),
                sub = stringResource(Res.string.switcher_join_sub),
                accent = false,
                onClick = onJoin,
            )
        }
    }
}

@Composable
private fun HouseholdRow(
    summary: HouseholdSummary,
    onClick: () -> Unit,
) {
    val tokens = LocalMindstockTokens.current
    val active = summary.active
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(tokens.radiusLg))
                .background(if (active) tokens.accentSoft else tokens.surface)
                .border(
                    BorderStroke(1.5.dp, if (active) tokens.accent else tokens.lineSoft),
                    RoundedCornerShape(tokens.radiusLg),
                ).clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (active) tokens.accent else tokens.surface2),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(AppIconName.Home, contentDescription = null, size = 22.dp, tint = if (active) tokens.onAccent else tokens.sub)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AppText(summary.name, style = MindstockType.cardTitle(), color = tokens.ink)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppIcon(AppIconName.Users, contentDescription = null, size = 13.dp, tint = tokens.faint)
                AppText(
                    stringResource(Res.string.settings_household_member_count, summary.memberCount),
                    style = MindstockType.unitCaption(),
                    color = tokens.faint,
                )
                AppText(SEPARATOR, style = MindstockType.unitCaption(), color = tokens.line)
                val ownerLike = summary.myRole.is世帯主()
                AppIcon(
                    roleIcon(summary.myRole),
                    contentDescription = null,
                    size = 12.dp,
                    tint = if (ownerLike) tokens.accent else tokens.faint,
                )
                AppText(
                    stringResource(roleLabelResource(summary.myRole)),
                    style = MindstockType.unitCaption(),
                    color = tokens.faint,
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (active) tokens.accent else Color.Transparent)
                    .border(2.dp, if (active) tokens.accent else tokens.line, RoundedCornerShape(99.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (active) AppIcon(AppIconName.Check, contentDescription = null, size = 14.dp, tint = tokens.onAccent)
        }
    }
}

@Composable
private fun SwitcherAction(
    icon: AppIconName,
    label: String,
    sub: String,
    accent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMindstockTokens.current
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(tokens.radiusLg))
                .background(tokens.surface)
                .border(
                    BorderStroke(1.dp, if (accent) tokens.accent else tokens.line),
                    RoundedCornerShape(tokens.radiusLg),
                ).clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (accent) tokens.accentSoft else tokens.surface2),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, contentDescription = null, size = 20.dp, tint = if (accent) tokens.accent else tokens.sub)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AppText(label, style = MindstockType.sectionMeta(), color = if (accent) tokens.accent else tokens.ink)
            AppText(sub, style = MindstockType.unitCaption(), color = tokens.faint)
        }
        AppIcon(AppIconName.ChevronRight, contentDescription = null, size = 17.dp, tint = tokens.faint)
    }
}
