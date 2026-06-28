package net.brightroom.mindstock.frontend.app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.nav_activity
import mindstock.frontend.generated.resources.nav_profile
import mindstock.frontend.generated.resources.nav_shop
import mindstock.frontend.generated.resources.nav_stock
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import org.jetbrains.compose.resources.StringResource

enum class Tab(
    val label: StringResource,
    val icon: AppIconName,
) {
    Stock(Res.string.nav_stock, AppIconName.Home),
    Shop(Res.string.nav_shop, AppIconName.Cart),
    Activity(Res.string.nav_activity, AppIconName.Clock),
    Profile(Res.string.nav_profile, AppIconName.User),
}

@Composable
private fun resolveShellKind(): ShellKind = shellKindFor(currentWindowAdaptiveInfoV2().windowSizeClass.minWidthDp)

@Composable
fun AppShell(
    selectedTab: Tab,
    onSelectTab: (Tab) -> Unit,
    onAdd: () -> Unit,
    onOpenSwitcher: () -> Unit,
    onBell: () -> Unit,
    displayName: String,
    householdName: String,
    stockContent: @Composable () -> Unit,
    shopContent: @Composable () -> Unit,
    activityContent: @Composable () -> Unit,
    profileContent: @Composable () -> Unit,
) {
    val kind = resolveShellKind()
    val content: @Composable () -> Unit = {
        CompositionLocalProvider(LocalIsWideShell provides (kind == ShellKind.Wide)) {
            when (selectedTab) {
                Tab.Stock -> stockContent()
                Tab.Shop -> shopContent()
                Tab.Activity -> activityContent()
                Tab.Profile -> profileContent()
            }
        }
    }
    when (kind) {
        ShellKind.Wide -> {
            WideShell(
                selectedTab = selectedTab,
                onSelectTab = onSelectTab,
                onAdd = onAdd,
                onOpenSwitcher = onOpenSwitcher,
                onBell = onBell,
                displayName = displayName,
                householdName = householdName,
                content = content,
            )
        }

        ShellKind.Compact -> {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                Box(modifier = Modifier.fillMaxSize().padding(bottom = 88.dp)) { content() }
                BottomNav(
                    selected = selectedTab,
                    onSelect = onSelectTab,
                    onAdd = onAdd,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}
