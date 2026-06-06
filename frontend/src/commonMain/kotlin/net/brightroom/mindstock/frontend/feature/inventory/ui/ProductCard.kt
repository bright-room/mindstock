package net.brightroom.mindstock.frontend.feature.inventory.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.action_consume
import mindstock.frontend.generated.resources.action_replenish
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.StockStatus
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.PrimaryButton
import net.brightroom.mindstock.frontend.designsystem.atom.StatusDot
import net.brightroom.mindstock.frontend.designsystem.atom.StockLevelBar
import net.brightroom.mindstock.frontend.designsystem.atom.Thumb
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
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
    val statusColor =
        when (stock.status()) {
            StockStatus.在庫切れ -> tokens.statusOut
            StockStatus.残りわずか -> tokens.statusLow
            StockStatus.十分 -> tokens.statusOk
        }
    Column(
        modifier = modifier.fillMaxWidth().clickable { onOpen(stock) }.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb()
            Column(modifier = Modifier.fillMaxWidth().padding(end = 8.dp)) {
                AppText(stock.product.name())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(color = statusColor)
                    AppText("${stock.currentQuantity()}${stock.product.setting.unit()}")
                }
            }
        }
        StockLevelBar(qty = stock.currentQuantity(), min = stock.product.setting.minimumStock(), color = statusColor)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(onClick = { onReplenish(stock) }) { AppText(stringResource(Res.string.action_replenish)) }
            PrimaryButton(onClick = { onConsume(stock) }) { AppText(stringResource(Res.string.action_consume)) }
        }
    }
}
