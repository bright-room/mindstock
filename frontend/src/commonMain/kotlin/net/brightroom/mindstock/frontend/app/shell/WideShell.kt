package net.brightroom.mindstock.frontend.app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.app_name
import mindstock.frontend.generated.resources.avatar_fallback_initial
import mindstock.frontend.generated.resources.sidebar_add_product
import mindstock.frontend.generated.resources.sidebar_notifications
import mindstock.frontend.generated.resources.sidebar_switch_subtitle
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.PrimaryButton
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import org.jetbrains.compose.resources.stringResource

/**
 * 幅 >= 840dp 用のデスクトップ shell。モック `app/app.jsx` の `DesktopChrome` 準拠。
 * 248dp 左サイドバー(ロゴ/世帯スイッチャ/追加/ナビ/お知らせ/ユーザフッタ) + content 中央寄せ。
 * ブラウザ枠(信号機ドット等)はモックのプレゼン足場であり再現しない(実ブラウザがそれ)。
 * 呼び出し側はアクティブな世帯がある(AuthState.Ready かつ householdId != null)ことを保証する(AppShell で制御)。
 */
@Composable
fun WideShell(
    selectedTab: Tab,
    onSelectTab: (Tab) -> Unit,
    onAdd: () -> Unit,
    onOpenSwitcher: () -> Unit,
    onBell: () -> Unit,
    displayName: String,
    householdName: String,
    content: @Composable () -> Unit,
) {
    val tokens = LocalMindstockTokens.current
    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // サイドバー
        Column(
            modifier =
                Modifier
                    .width(248.dp)
                    .fillMaxHeight()
                    .background(tokens.surface)
                    .padding(horizontal = 16.dp, vertical = 24.dp),
        ) {
            // ロゴ
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(36.dp)
                            .rotate(-6f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(tokens.accent),
                    contentAlignment = Alignment.Center,
                ) { AppIcon(AppIconName.Box, contentDescription = null, tint = tokens.onAccent, size = 20.dp) }
                AppText(
                    stringResource(Res.string.app_name),
                    style = MindstockType.summaryTitle().copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold),
                    color = tokens.ink,
                )
            }

            // 世帯スイッチャ
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, tokens.line, RoundedCornerShape(12.dp))
                        .background(tokens.surface2)
                        .clickable(onClick = onOpenSwitcher)
                        .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(tokens.accentSoft),
                    contentAlignment = Alignment.Center,
                ) { AppIcon(AppIconName.Home, contentDescription = null, tint = tokens.accent, size = 17.dp) }
                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        householdName,
                        style = MindstockType.cardTitle().copy(fontSize = 13.5.sp, fontWeight = FontWeight.Bold),
                        color = tokens.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AppText(stringResource(Res.string.sidebar_switch_subtitle), style = MindstockType.unitCaption(), color = tokens.faint)
                }
                AppIcon(AppIconName.ChevronDown, contentDescription = null, tint = tokens.faint, size = 15.dp)
            }

            Spacer(Modifier.height(16.dp))

            // 商品を追加
            PrimaryButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                AppIcon(AppIconName.Plus, contentDescription = null, tint = tokens.onAccent, size = 18.dp)
                Spacer(Modifier.width(8.dp))
                AppText(stringResource(Res.string.sidebar_add_product), color = tokens.onAccent, style = MindstockType.button())
            }

            Spacer(Modifier.height(18.dp))

            // ナビ
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Tab.entries.forEach { tab ->
                    val active = tab == selectedTab
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) tokens.accentSoft else Color.Transparent)
                                .clickable { onSelectTab(tab) }
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppIcon(tab.icon, contentDescription = null, tint = if (active) tokens.accent else tokens.sub, size = 20.dp)
                        AppText(
                            stringResource(tab.label),
                            style = MindstockType.button().copy(fontSize = 14.5.sp),
                            color = if (active) tokens.accent else tokens.sub,
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // お知らせ(bell) — 通知機能は将来。Spec1 は present-but-no-op。
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onBell)
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppIcon(AppIconName.Bell, contentDescription = null, tint = tokens.sub, size = 20.dp)
                AppText(stringResource(Res.string.sidebar_notifications), style = MindstockType.button(), color = tokens.sub)
            }

            // 区切り線 + ユーザフッタ
            Box(Modifier.fillMaxWidth().height(1.dp).background(tokens.lineSoft))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Box(
                    modifier = Modifier.size(34.dp).clip(CircleShape).background(tokens.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(
                        displayName.take(1).ifEmpty { stringResource(Res.string.avatar_fallback_initial) },
                        color = tokens.onAccent,
                        style = MindstockType.statusLabel(),
                    )
                }
                Column {
                    AppText(
                        displayName,
                        style = MindstockType.cardTitle().copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                        color = tokens.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AppText(
                        householdName,
                        style = MindstockType.unitCaption(),
                        color = tokens.faint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // サイドバーと content の境界線(mock borderRight 1px lineSoft)
        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(tokens.lineSoft))

        // content(中央寄せ・最大 880dp)
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Box(modifier = Modifier.widthIn(max = 880.dp).fillMaxHeight().align(Alignment.TopCenter)) {
                content()
            }
        }
    }
}
