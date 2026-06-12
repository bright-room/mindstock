package net.brightroom.mindstock.frontend.feature.notification

import net.brightroom.mindstock.domain.model.inventory.stock.ConsumptionForecast
import net.brightroom.mindstock.domain.model.inventory.stock.EvaluatedTime
import net.brightroom.mindstock.domain.model.inventory.stock.StockStatus
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks

/** 上限。mock NotifSheet の .slice(0, 6) に対応。 */
private const val MAX_ALERTS = 6

/** 予測日数の閾値。mock の d <= 5 に対応。 */
private const val SOON_DAYS = 5

/**
 * 在庫からお知らせ用アラートを導出する。mock app/screens-c.jsx NotifSheet と同条件。
 * status 優先(在庫切れ/残りわずかは forecast によらず status 理由)。十分なら forecast<=5 のみ拾う。
 *
 * 非 @Composable の純粋なドメイン→表示変換。`feature/` 直下に置くのは意図的で、
 * 既存の同種関数(`inventory/StockSummary.kt` の stockSummaryOf、`activity/ActivityGrouping.kt` の
 * groupedByDay 等)と同じ配置方針に揃えている。
 */
fun stockAlerts(
    stocks: Stocks,
    asOf: EvaluatedTime,
): List<StockAlert> =
    stocks.list
        .mapNotNull { stock ->
            when (stock.status()) {
                StockStatus.在庫切れ -> {
                    StockAlert(stock, AlertReason.OutOfStock)
                }

                StockStatus.残りわずか -> {
                    StockAlert(stock, AlertReason.RunningLow)
                }

                StockStatus.十分 -> {
                    when (val forecast = stock.forecast(asOf)) {
                        is ConsumptionForecast.DaysRemaining -> {
                            val days = forecast()
                            if (days <= SOON_DAYS) StockAlert(stock, AlertReason.RunningOutSoon(days)) else null
                        }

                        ConsumptionForecast.Unknown -> {
                            null
                        }
                    }
                }
            }
        }.take(MAX_ALERTS)
