package net.brightroom.mindstock.frontend.app.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.profile_archived_entry
import mindstock.frontend.generated.resources.profile_archived_entry_sub
import mindstock.frontend.generated.resources.profile_master_entry
import mindstock.frontend.generated.resources.profile_master_entry_sub
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProfileScreen(
    isOwner: Boolean,
    onOpenMaster: () -> Unit,
    onOpenArchived: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (isOwner) {
            ProfileRow(
                AppIconName.Settings,
                stringResource(Res.string.profile_master_entry),
                stringResource(Res.string.profile_master_entry_sub),
                onOpenMaster,
            )
        }
        ProfileRow(
            AppIconName.Archive,
            stringResource(Res.string.profile_archived_entry),
            stringResource(Res.string.profile_archived_entry_sub),
            onOpenArchived,
        )
    }
}

@Composable
private fun ProfileRow(
    icon: AppIconName,
    title: String,
    sub: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppIcon(icon, contentDescription = null)
        Column(modifier = Modifier.weight(1f)) {
            AppText(title, style = MindstockType.summaryTitle())
            AppText(sub, style = MindstockType.sectionMeta())
        }
        AppIcon(AppIconName.ChevronRight, contentDescription = null)
    }
}
