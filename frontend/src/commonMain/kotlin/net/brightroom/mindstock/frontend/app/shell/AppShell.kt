package net.brightroom.mindstock.frontend.app.shell

import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.nav_activity
import mindstock.frontend.generated.resources.nav_profile
import mindstock.frontend.generated.resources.nav_shop
import mindstock.frontend.generated.resources.nav_stock
import mindstock.frontend.generated.resources.tab_activity_placeholder
import mindstock.frontend.generated.resources.tab_profile_placeholder
import mindstock.frontend.generated.resources.tab_shop_placeholder
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

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
fun AppShell(stockContent: @Composable () -> Unit) {
    var selected by remember { mutableStateOf(Tab.Stock) }
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            Tab.entries.forEach { tab ->
                item(
                    selected = tab == selected,
                    onClick = { selected = tab },
                    icon = { AppIcon(tab.icon, contentDescription = stringResource(tab.label)) },
                    label = { AppText(stringResource(tab.label)) },
                )
            }
        },
    ) {
        when (selected) {
            Tab.Stock -> stockContent()
            Tab.Shop -> AppText(stringResource(Res.string.tab_shop_placeholder))
            Tab.Activity -> AppText(stringResource(Res.string.tab_activity_placeholder))
            Tab.Profile -> AppText(stringResource(Res.string.tab_profile_placeholder))
        }
    }
}
