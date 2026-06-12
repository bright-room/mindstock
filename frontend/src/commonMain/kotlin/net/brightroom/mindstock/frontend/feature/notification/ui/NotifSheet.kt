package net.brightroom.mindstock.frontend.feature.notification.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.notif_alert_low
import mindstock.frontend.generated.resources.notif_alert_out
import mindstock.frontend.generated.resources.notif_alert_soon
import mindstock.frontend.generated.resources.notif_subtitle
import mindstock.frontend.generated.resources.notif_title
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.Sheet
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import net.brightroom.mindstock.frontend.feature.notification.AlertReason
import net.brightroom.mindstock.frontend.feature.notification.StockAlert
import org.jetbrains.compose.resources.stringResource

/**
 * お知らせ(在庫アラート一覧)シート。mock app/screens-c.jsx NotifSheet 準拠。
 * client 派生(サーバ通知なし)。行タップで onOpen(stock) → 商品詳細へ。
 */
@Composable
fun NotifSheet(
    open: Boolean,
    alerts: List<StockAlert>,
    onClose: () -> Unit,
    onOpen: (Stock) -> Unit,
) {
    val tokens = LocalMindstockTokens.current
    Sheet(open = open, title = stringResource(Res.string.notif_title), onClose = onClose) {
        Column {
            AppText(
                text = stringResource(Res.string.notif_subtitle),
                style = MindstockType.summarySub().copy(fontWeight = FontWeight.Normal, fontSize = 12.sp),
                color = tokens.faint,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                alerts.forEach { alert ->
                    AlertRow(alert = alert, onClick = {
                        onClose()
                        onOpen(alert.stock)
                    })
                }
            }
        }
    }
}

@Composable
private fun AlertRow(
    alert: StockAlert,
    onClick: () -> Unit,
) {
    val tokens = LocalMindstockTokens.current
    val (iconBg, iconColor) =
        when (alert.reason) {
            AlertReason.OutOfStock -> tokens.statusOutSoft to tokens.statusOut
            AlertReason.RunningLow -> tokens.statusLowSoft to tokens.statusLow
            is AlertReason.RunningOutSoon -> tokens.statusOkSoft to tokens.statusOk
        }
    val icon = if (alert.reason is AlertReason.OutOfStock) AppIconName.Cart else AppIconName.Trend
    val message =
        when (val reason = alert.reason) {
            AlertReason.OutOfStock -> stringResource(Res.string.notif_alert_out)
            AlertReason.RunningLow -> stringResource(Res.string.notif_alert_low)
            is AlertReason.RunningOutSoon -> stringResource(Res.string.notif_alert_soon, reason.days)
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(tokens.radiusMd))
                .border(1.dp, tokens.lineSoft, RoundedCornerShape(tokens.radiusMd))
                .background(tokens.surface)
                .clickable(onClick = onClick)
                .padding(13.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(iconBg),
        ) {
            AppIcon(icon, contentDescription = null, tint = iconColor, size = 19.dp)
        }
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = alert.stock.product.name(),
                style = MindstockType.cardTitle().copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                color = tokens.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AppText(
                text = message,
                style = MindstockType.summarySub().copy(fontWeight = FontWeight.Medium, fontSize = 12.sp),
                color = tokens.faint,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        AppIcon(AppIconName.ChevronRight, contentDescription = null, tint = tokens.faint, size = 17.dp)
    }
}
