package net.brightroom.mindstock.domain.model.stock

import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.stock.movement.Note
import net.brightroom.mindstock.domain.model.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.stock.movement.Quantity
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
class StocksTest {
    private val profile =
        Profile(
            userId = UserId(Uuid.generateV7()),
            displayName = DisplayName("alice"),
        )
    private val now = Instant.parse("2026-05-24T10:00:00Z")

    private fun stockOf(
        min: Int,
        currentReplenished: Int,
    ): Stock {
        val product =
            Product(
                id = ProductId(Uuid.generateV7()),
                catalogItem =
                    CatalogItem(
                        id = CatalogItemId(Uuid.generateV7()),
                        name = CatalogItemName("item"),
                        unit = CatalogItemUnit("個"),
                    ),
                minimumStock = MinimumStock.Set(min),
                archived = false,
            )
        val movements =
            if (currentReplenished > 0) {
                listOf(
                    Replenishment(
                        quantity = Quantity(currentReplenished),
                        occurredAt = OccurredAt(Instant.parse("2026-05-23T10:00:00Z")),
                        actor = profile,
                        note = Note(""),
                    ),
                )
            } else {
                emptyList()
            }
        return Stock(product = product, movements = StockMovements(movements))
    }

    @Test
    fun `list is exposed directly`() {
        val s1 = stockOf(min = 1, currentReplenished = 0)
        val s2 = stockOf(min = 1, currentReplenished = 5)
        val stocks = Stocks(listOf(s1, s2))

        stocks.list shouldBe listOf(s1, s2)
    }
}
