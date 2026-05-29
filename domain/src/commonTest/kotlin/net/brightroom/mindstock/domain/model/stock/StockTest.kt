package net.brightroom.mindstock.domain.model.stock

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.stock.movement.Consumption
import net.brightroom.mindstock.domain.model.stock.movement.Replenishment
import net.brightroom.mindstock.domain.model.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile
import kotlin.test.Test
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class StockTest {
    private val profile =
        Profile(
            userId = UserId(Uuid.generateV7()),
            displayName = DisplayName("alice"),
        )

    private fun productWithMin(min: Int?) =
        Product(
            id = ProductId(Uuid.generateV7()),
            catalogItem =
                CatalogItem(
                    id = CatalogItemId(Uuid.generateV7()),
                    name = CatalogItemName("ハンドソープ"),
                    unit = CatalogItemUnit("本"),
                ),
            minimumStock = if (min != null) MinimumStock.Set(min) else MinimumStock.NotSet,
            archived = false,
        )

    private val now = Instant.parse("2026-05-24T10:00:00Z")

    private fun occurred() =
        OccurredAt(
            LocalDateTime(2026, 5, 1, 10, 0).toInstant(TimeZone.UTC),
            now,
        )

    private fun replenish(
        product: Product,
        qty: Int,
    ) = Replenishment(Quantity(qty), occurred(), profile, Note(""))

    private fun consume(
        product: Product,
        qty: Int,
    ) = Consumption(Quantity(qty), occurred(), profile, Note(""))

    @Test
    fun `currentQuantity is sum of replenishments minus consumptions`() {
        val p = productWithMin(null)
        val stock =
            Stock(
                product = p,
                movements = StockMovements(listOf(replenish(p, 5), replenish(p, 3), consume(p, 2))),
            )
        stock.currentQuantity() shouldBe 6
    }

    @Test
    fun `currentQuantity is zero when no movements`() {
        val p = productWithMin(null)
        Stock(p, StockMovements(emptyList())).currentQuantity() shouldBe 0
    }

    @Test
    fun `needsReplenishment is true when current quantity is below minimum`() {
        val p = productWithMin(5)
        val stock = Stock(p, StockMovements(listOf(replenish(p, 3))))
        stock.needsReplenishment().shouldBeTrue()
        stock.shortage() shouldBe 2
    }

    @Test
    fun `needsReplenishment is false when minimumStock is NotSet`() {
        val p = productWithMin(null)
        val stock = Stock(p, StockMovements(emptyList()))
        stock.needsReplenishment().shouldBeFalse()
    }

    @Test
    fun `correction is expressed as an additional consumption movement`() {
        // ユーザーが 3 個補充したつもりが 2 個だった場合、訂正用 API は無く、
        // 単に消費 1 を追加することで在庫の整合性を取る(訂正概念廃止の意図表現)。
        val p = productWithMin(null)
        val stock =
            Stock(
                product = p,
                movements = StockMovements(listOf(replenish(p, 3), consume(p, 1))),
            )
        stock.currentQuantity() shouldBe 2
    }
}
