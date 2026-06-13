package net.brightroom.mindstock.frontend.feature.inventory.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.summary_need_sub
import mindstock.frontend.generated.resources.summary_need_sub_want
import mindstock.frontend.generated.resources.summary_need_title
import mindstock.frontend.generated.resources.summary_ok_sub
import mindstock.frontend.generated.resources.summary_ok_title
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import net.brightroom.mindstock.frontend.designsystem.theme.ShadowLevel
import net.brightroom.mindstock.frontend.designsystem.theme.softShadow
import net.brightroom.mindstock.frontend.feature.inventory.StockSummary
import org.jetbrains.compose.resources.stringResource

/** 買い物 CTA。need 件数で accent/surface を切替。 */
@Composable
fun SummaryStrip(
    summary: StockSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMindstockTokens.current
    val need = summary.needCount > 0
    val shape = RoundedCornerShape(22.dp)
    val container = if (need) tokens.accent else tokens.surface
    val onContainer = if (need) tokens.onAccent else tokens.ink
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .softShadow(if (need) ShadowLevel.Md else ShadowLevel.Sm, shape)
                .clip(shape)
                .background(container)
                .clickable(onClick = onClick)
                .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (need) tokens.onAccent.copy(alpha = 0.18f) else tokens.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(
                AppIconName.Cart,
                contentDescription = null,
                size = 24.dp,
                tint = if (need) tokens.onAccent else tokens.accent,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AppText(
                if (need) {
                    stringResource(Res.string.summary_need_title, summary.needCount)
                } else {
                    stringResource(Res.string.summary_ok_title)
                },
                style = MindstockType.summaryTitle(),
                color = onContainer,
            )
            AppText(
                if (need) {
                    // モック準拠: 手動希望(want)があれば「・ 自分で追加 N」を付ける(need 件数の内訳一致)。
                    if (summary.wantCount > 0) {
                        stringResource(Res.string.summary_need_sub_want, summary.outCount, summary.lowCount, summary.wantCount)
                    } else {
                        stringResource(Res.string.summary_need_sub, summary.outCount, summary.lowCount)
                    }
                } else {
                    stringResource(Res.string.summary_ok_sub)
                },
                style = MindstockType.summarySub(),
                color = onContainer.copy(alpha = 0.82f),
            )
        }
        AppIcon(
            AppIconName.ChevronRight,
            contentDescription = null,
            size = 20.dp,
            tint = onContainer.copy(alpha = 0.7f),
        )
    }
}
