package net.brightroom.mindstock.frontend.app.shell

import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText

enum class Tab(
    val label: String,
    val icon: AppIconName,
) {
    Stock("在庫", AppIconName.Home),
    Shop("買い物", AppIconName.Cart),
    Activity("履歴", AppIconName.Clock),
    Profile("設定", AppIconName.User),
}

@Composable
fun AppShell(stockContent: @Composable () -> Unit) {
    var selected by remember { mutableStateOf(Tab.Stock) }
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            Tab.entries.forEach { tab ->
                item(
                    selected = tab == selected,
                    onClick = { selected = tab },
                    icon = { AppIcon(tab.icon, contentDescription = tab.label) },
                    label = { AppText(tab.label) },
                )
            }
        },
    ) {
        when (selected) {
            Tab.Stock -> stockContent()
            Tab.Shop -> AppText("買い物(P6-1)")
            Tab.Activity -> AppText("履歴(P6-1)")
            Tab.Profile -> AppText("設定(P6-3)")
        }
    }
}
