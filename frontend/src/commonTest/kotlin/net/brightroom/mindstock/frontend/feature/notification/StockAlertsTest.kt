package net.brightroom.mindstock.frontend.feature.notification

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.EvaluatedTime
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementIdentity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement.Consumption
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement.Replenishment
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.ResidentProfile
import kotlin.test.Test

class StockAlertsTest {
    // テスト中は固定時刻を使うことで予測値を決定論的にする
    private val asOf = EvaluatedTime(LocalDateTime(2026, 6, 12, 12, 0))

    private fun actor() = Resident(ResidentId.create(), ResidentProfile(DisplayName("テスト")))

    private fun daysAgo(n: Int): OccurredAt {
        val base = asOf().date.minus(DatePeriod(days = n))
        return OccurredAt(LocalDateTime(base, LocalTime(12, 0)))
    }

    private fun replenish(
        id: Long,
        qty: Int,
        daysAgo: Int,
    ) = Replenishment(MovementIdentity.Persisted(MovementId(id)), Quantity(qty), daysAgo(daysAgo), actor(), Note(""))

    private fun consume(
        id: Long,
        qty: Int,
        daysAgo: Int,
    ) = Consumption(MovementIdentity.Persisted(MovementId(id)), Quantity(qty), daysAgo(daysAgo), actor(), Note(""))

    /**
     * currentQuantity=0 → status=在庫切れ
     */
    private fun outOfStockStock(): Stock {
        val product = Product.custom(ProductName("品切れ商品"), Barcode.Unlinked, ProductUnit("個"), MinimumStock(1))
        return Stock(product, StockMovements(emptyList()))
        // currentQuantity=0 → StockStatus.在庫切れ
    }

    /**
     * currentQuantity=1, minimumStock=2 → 0 < 1 <= 2 → status=残りわずか
     */
    private fun runningLowStock(): Stock {
        val product = Product.custom(ProductName("残りわずか商品"), Barcode.Unlinked, ProductUnit("個"), MinimumStock(2))
        return Stock(product, StockMovements(listOf(replenish(1, 1, 10))))
        // currentQuantity=1, minimum=2 → StockStatus.残りわずか
    }

    /**
     * currentQuantity=2, minimumStock=1 → status=十分
     * 消費ペース: span は最古 movement(=補充 10 日前)から asOf まで → max(1, 10)=10 日。
     * span(10) < FORECAST_WINDOW_DAYS(60) なので全履歴平均: consumed=8 / span=10 → 0.8/日。
     * 予測: round(2 / 0.8) = round(2.5) = 3 日 ≤ 5 → RunningOutSoon
     */
    private fun soonStock(): Stock {
        val product = Product.custom(ProductName("もうすぐ切れる商品"), Barcode.Unlinked, ProductUnit("個"), MinimumStock(1))
        return Stock(
            product,
            StockMovements(
                listOf(
                    replenish(1, 10, 10),
                    consume(2, 8, 5),
                ),
            ),
        )
        // currentQuantity=2, minimum=1 → StockStatus.十分
        // forecast: span=10日(最古=補充10日前)・consumed=8 → rate=0.8/日 → days=round(2/0.8)=3 ≤ 5
    }

    /**
     * currentQuantity=29, minimumStock=1 → status=十分
     * 消費ペース: span は最古 movement(=補充 10 日前)から asOf まで → max(1, 10)=10 日。
     * span(10) < FORECAST_WINDOW_DAYS(60) なので全履歴平均: consumed=1 / span=10 → 0.1/日。
     * 予測: round(29 / 0.1) = round(290.0) = 290 日 > 5 → 除外
     */
    private fun farStock(): Stock {
        val product = Product.custom(ProductName("当分余裕がある商品"), Barcode.Unlinked, ProductUnit("個"), MinimumStock(1))
        return Stock(
            product,
            StockMovements(
                listOf(
                    replenish(1, 30, 10),
                    consume(2, 1, 5),
                ),
            ),
        )
        // currentQuantity=29, minimum=1 → StockStatus.十分
        // forecast: span=10日(最古=補充10日前)・consumed=1 → rate=0.1/日 → days=round(29/0.1)=290 > 5 → 除外
    }

    /**
     * currentQuantity=5, minimumStock=1 → status=十分
     * 消費履歴なし → forecast=Unknown → アラート除外
     */
    private fun healthyStock(): Stock {
        val product = Product.custom(ProductName("十分な商品"), Barcode.Unlinked, ProductUnit("個"), MinimumStock(1))
        return Stock(product, StockMovements(listOf(replenish(1, 5, 10))))
        // currentQuantity=5, minimum=1 → StockStatus.十分
        // 消費履歴なし → ConsumptionForecast.Unknown → アラート除外
    }

    @Test
    fun 在庫切れは_OutOfStock() {
        val alerts = stockAlerts(Stocks(listOf(outOfStockStock())), asOf)
        alerts shouldHaveSize 1
        alerts.first().reason shouldBe AlertReason.OutOfStock
    }

    @Test
    fun 残りわずかは_RunningLow() {
        stockAlerts(Stocks(listOf(runningLowStock())), asOf).first().reason shouldBe AlertReason.RunningLow
    }

    @Test
    fun 十分かつ予測5日以内は_RunningOutSoon() {
        stockAlerts(Stocks(listOf(soonStock())), asOf).first().reason.shouldBeInstanceOf<AlertReason.RunningOutSoon>()
    }

    @Test
    fun 十分かつ予測なしは除外() {
        stockAlerts(Stocks(listOf(healthyStock())), asOf) shouldHaveSize 0
    }

    @Test
    fun 十分かつ予測6日以上は除外() {
        // farStock の forecast=290日(> 5)なのでアラートに含まれない
        stockAlerts(Stocks(listOf(farStock())), asOf) shouldHaveSize 0
    }

    @Test
    fun 先頭6件に切り詰める() {
        stockAlerts(Stocks(List(8) { outOfStockStock() }), asOf) shouldHaveSize 6
    }

    @Test
    fun 空在庫は空リスト() {
        stockAlerts(Stocks(emptyList()), asOf) shouldHaveSize 0
    }
}
