package net.brightroom.mindstock.domain.model.inventory.stock

import io.kotest.matchers.shouldBe
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.ProductStatus
import net.brightroom.mindstock.domain.model.inventory.product.image.ProductImage
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.product.setting.StockingPolicy
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementIdentity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Reason
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement.Consumption
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement.Replenishment
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import kotlin.test.Test

class StockForecastTest {
    private val asOf = LocalDateTime(2026, 6, 8, 12, 0)

    private fun actor() = Resident(ResidentId.create(), Profile(DisplayName("おや")))

    private fun product() =
        Product(
            id = ProductId.create(),
            name = ProductName("米"),
            barcode = Barcode.Unlinked,
            setting = StockingPolicy(ProductUnit("袋"), MinimumStock(1)),
            image = ProductImage.None,
            status = ProductStatus.採用中,
        )

    /** asOf の n 日前(正午)の OccurredAt。 */
    private fun daysAgo(n: Int): OccurredAt = OccurredAt(LocalDateTime(asOf.date.minus(DatePeriod(days = n)), LocalTime(12, 0)))

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

    private fun stockOf(vararg movements: StockMovement) = Stock(product(), StockMovements(movements.toList()))

    @Test
    fun A_定常はトレーリング窓のレートで予測する() {
        val stock = stockOf(replenish(1, 15, 120), consume(2, 12, 30))
        stock.currentQuantity() shouldBe 3
        stock.forecast(asOf) shouldBe ConsumptionForecast.DaysRemaining(15)
    }

    @Test
    fun B_直近窓に消費が無ければ全履歴平均にfallbackする() {
        val stock = stockOf(replenish(1, 7, 100), consume(2, 5, 80))
        stock.currentQuantity() shouldBe 2
        stock.forecast(asOf) shouldBe ConsumptionForecast.DaysRemaining(40)
    }

    @Test
    fun C_履歴の浅い新商品はspanベースで予測する() {
        val stock = stockOf(replenish(1, 10, 10), consume(2, 6, 5))
        stock.currentQuantity() shouldBe 4
        stock.forecast(asOf) shouldBe ConsumptionForecast.DaysRemaining(7)
    }

    @Test
    fun D_消費実績が無ければUnknown() {
        val stock = stockOf(replenish(1, 5, 10))
        stock.currentQuantity() shouldBe 5
        stock.forecast(asOf) shouldBe ConsumptionForecast.Unknown
    }

    @Test
    fun E_在庫が0以下ならUnknown() {
        val stock = stockOf(replenish(1, 3, 10), consume(2, 3, 5))
        stock.currentQuantity() shouldBe 0
        stock.forecast(asOf) shouldBe ConsumptionForecast.Unknown
    }

    @Test
    fun F_訂正後の実効消費量でレートを算出する() {
        val stock =
            stockOf(replenish(1, 20, 20), consume(10, 4, 10))
                .correct(MovementId(10), Quantity(2), Reason("数え直し"), actor(), daysAgo(1))
        stock.currentQuantity() shouldBe 18
        stock.forecast(asOf) shouldBe ConsumptionForecast.DaysRemaining(180)
    }

    @Test
    fun 窓境界_ちょうど60日前の消費はトレーリングに含む() {
        val stock = stockOf(replenish(1, 100, 120), consume(2, 10, 60))
        stock.currentQuantity() shouldBe 90
        stock.forecast(asOf) shouldBe ConsumptionForecast.DaysRemaining(540)
    }

    @Test
    fun トレーリング窓は窓外の古い消費を除外する() {
        // 補充100(90日前)・消費30(90日前=窓外)・消費10(30日前=窓内)→ 在庫60・span90(≥60)・recent10
        // → トレーリング rate=10/60 → round(60/(10/60))=360。窓外の30を含めるバグなら値が変わる。
        val stock = stockOf(replenish(1, 100, 90), consume(2, 30, 90), consume(3, 10, 30))
        stock.currentQuantity() shouldBe 60
        stock.forecast(asOf) shouldBe ConsumptionForecast.DaysRemaining(360)
    }

    @Test
    fun span1日クランプで0除算しない() {
        val stock = stockOf(replenish(1, 5, 0), consume(2, 2, 0))
        stock.currentQuantity() shouldBe 3
        stock.forecast(asOf) shouldBe ConsumptionForecast.DaysRemaining(2)
    }
}
