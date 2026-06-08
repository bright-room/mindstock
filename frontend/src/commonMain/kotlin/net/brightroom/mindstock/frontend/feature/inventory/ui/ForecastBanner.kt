package net.brightroom.mindstock.frontend.feature.inventory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDateTime
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.forecast_banner
import net.brightroom.mindstock.domain.model.inventory.stock.ConsumptionForecast
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.extensions.kotlinx.datetime.now
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import org.jetbrains.compose.resources.stringResource

/**
 * 在庫ホームの予測バナー。在庫 > 0 で最も早く切れる見込みの商品を 1 件表示する。
 * 予測可能な商品が無ければ何も描画しない。
 */
@Composable
fun ForecastBanner(
    stocks: Stocks,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMindstockTokens.current
    val now = LocalDateTime.now()
    val soon =
        stocks.list
            .mapNotNull { stock ->
                when (val f = stock.forecast(now)) {
                    is ConsumptionForecast.DaysRemaining -> stock to f.days
                    ConsumptionForecast.Unknown -> null
                }
            }.minByOrNull { it.second } ?: return

    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(tokens.surface)
                .border(1.dp, tokens.lineSoft, shape)
                .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(AppIconName.Trend, contentDescription = null, size = 17.dp, tint = tokens.accent)
        AppText(
            stringResource(
                Res.string.forecast_banner,
                soon.first.product
                    .name()
                    .substringBefore(' '),
                soon.second,
            ),
            style = MindstockType.sectionMeta(),
            color = tokens.sub,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
