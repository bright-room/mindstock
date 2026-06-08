package net.brightroom.mindstock.frontend.app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import net.brightroom.mindstock.frontend.designsystem.theme.ShadowLevel
import net.brightroom.mindstock.frontend.designsystem.theme.softShadow
import org.jetbrains.compose.resources.stringResource

/**
 * モックの浮遊グラスピル風の下部ナビ。
 * Compose/Wasm に真の backdrop-blur は無いため、半透明 surface + 影 + 角丸 + 側余白で近似する。
 */
@Composable
fun BottomNav(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMindstockTokens.current
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp)
                .softShadow(ShadowLevel.Lg, shape)
                .clip(shape)
                .background(tokens.surface.copy(alpha = 0.82f))
                .border(1.dp, tokens.lineSoft, shape)
                .height(62.dp)
                .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        NavItem(Tab.Stock, selected, onSelect, Modifier.weight(1f))
        NavItem(Tab.Shop, selected, onSelect, Modifier.weight(1f))
        CenterFab(onAdd)
        NavItem(Tab.Activity, selected, onSelect, Modifier.weight(1f))
        NavItem(Tab.Profile, selected, onSelect, Modifier.weight(1f))
    }
}

@Composable
private fun NavItem(
    tab: Tab,
    selected: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMindstockTokens.current
    val active = tab == selected
    val color = if (active) tokens.accent else tokens.faint
    Column(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).clickable { onSelect(tab) }.padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AppIcon(tab.icon, contentDescription = stringResource(tab.label), tint = color, size = 22.dp)
        AppText(stringResource(tab.label), style = MindstockType.statusLabel().copy(fontSize = 10.sp), color = color)
    }
}

@Composable
private fun CenterFab(onAdd: () -> Unit) {
    val tokens = LocalMindstockTokens.current
    Box(
        modifier =
            Modifier
                .size(50.dp)
                .offset(y = (-2).dp)
                .softShadow(ShadowLevel.Md, RoundedCornerShape(17.dp))
                .clip(RoundedCornerShape(17.dp))
                .background(tokens.accent)
                .clickable(onClick = onAdd),
        contentAlignment = Alignment.Center,
    ) { AppIcon(AppIconName.Plus, contentDescription = null, tint = tokens.onAccent, size = 26.dp) }
}
