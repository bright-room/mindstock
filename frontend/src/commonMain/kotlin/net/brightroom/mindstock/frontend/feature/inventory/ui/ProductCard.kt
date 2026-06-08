package net.brightroom.mindstock.frontend.feature.inventory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.action_consume
import mindstock.frontend.generated.resources.action_replenish
import mindstock.frontend.generated.resources.status_low
import mindstock.frontend.generated.resources.status_ok
import mindstock.frontend.generated.resources.status_out
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.StockStatus
import net.brightroom.mindstock.frontend.designsystem.atom.AppButton
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.ButtonSize
import net.brightroom.mindstock.frontend.designsystem.atom.ButtonVariant
import net.brightroom.mindstock.frontend.designsystem.atom.StatusDot
import net.brightroom.mindstock.frontend.designsystem.atom.StockLevelBar
import net.brightroom.mindstock.frontend.designsystem.atom.Thumb
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import net.brightroom.mindstock.frontend.designsystem.theme.ShadowLevel
import net.brightroom.mindstock.frontend.designsystem.theme.softShadow
import net.brightroom.mindstock.frontend.feature.inventory.glyphForProductName
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProductCard(
    stock: Stock,
    onOpen: (Stock) -> Unit,
    onReplenish: (Stock) -> Unit,
    onConsume: (Stock) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMindstockTokens.current
    val status = stock.status()
    val statusColor =
        when (status) {
            StockStatus.在庫切れ -> tokens.statusOut
            StockStatus.残りわずか -> tokens.statusLow
            StockStatus.十分 -> tokens.statusOk
        }
    val statusSoft =
        when (status) {
            StockStatus.在庫切れ -> tokens.statusOutSoft
            StockStatus.残りわずか -> tokens.statusLowSoft
            StockStatus.十分 -> tokens.statusOkSoft
        }
    val statusLabel =
        when (status) {
            StockStatus.在庫切れ -> stringResource(Res.string.status_out)
            StockStatus.残りわずか -> stringResource(Res.string.status_low)
            StockStatus.十分 -> stringResource(Res.string.status_ok)
        }
    val shape = RoundedCornerShape(22.dp)
    val qty = stock.currentQuantity()
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .softShadow(ShadowLevel.Sm, shape)
                .clip(shape)
                .background(tokens.surface)
                .border(1.dp, tokens.lineSoft, shape)
                .clickable { onOpen(stock) }
                .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            Thumb(icon = glyphForProductName(stock.product.name()), size = 48.dp)
            Column(Modifier.weight(1f)) {
                AppText(
                    stock.product.name(),
                    style = MindstockType.cardTitle(),
                    color = tokens.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                StatusDot(color = statusColor, soft = statusSoft, label = statusLabel)
            }
            Column(horizontalAlignment = Alignment.End) {
                AppText(
                    "$qty",
                    style = MindstockType.bigQty(),
                    color = if (status == StockStatus.在庫切れ) tokens.statusOut else tokens.ink,
                )
                AppText(stock.product.setting.unit(), style = MindstockType.unitCaption(), color = tokens.faint)
            }
        }
        // モック準拠: 十分(ok)のバーは status色(緑)でなくアクセント(橙)。
        StockLevelBar(
            qty = qty,
            min = stock.product.setting.minimumStock(),
            color = if (status == StockStatus.十分) tokens.accent else statusColor,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            AppButton(
                onClick = { onReplenish(stock) },
                variant = ButtonVariant.Soft,
                size = ButtonSize.Sm,
                icon = AppIconName.Plus,
                modifier = Modifier.weight(1f),
            ) { AppText(stringResource(Res.string.action_replenish)) }
            AppButton(
                onClick = { onConsume(stock) },
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Sm,
                icon = AppIconName.Minus,
                modifier = Modifier.weight(1f),
            ) { AppText(stringResource(Res.string.action_consume)) }
        }
    }
}
