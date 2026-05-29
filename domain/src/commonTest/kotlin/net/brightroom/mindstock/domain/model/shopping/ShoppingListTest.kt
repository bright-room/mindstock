package net.brightroom.mindstock.domain.model.shopping

import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.Stock
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
class ShoppingListTest {
    private val profile =
        Profile(
            UserId(Uuid.generateV7()),
            DisplayName("alice"),
        )
    private val now = Instant.parse("2026-05-24T10:00:00Z")

    private fun stockOf(
        name: String,
        min: Int,
        currentReplenished: Int,
    ): Stock {
        val product =
            Product(
                id = ProductId(Uuid.generateV7()),
                catalogItem =
                    CatalogItem(
                        id = CatalogItemId(Uuid.generateV7()),
                        name = CatalogItemName(name),
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
                        occurredAt = OccurredAt(Instant.parse("2026-05-23T10:00:00Z"), now),
                        actor = profile,
                        note = Note(""),
                    ),
                )
            } else {
                emptyList()
            }
        return Stock(
            product = product,
            movements = StockMovements(movements),
        )
    }

    @Test
    fun `itemsToBuy returns only stocks below minimum`() {
        val low = stockOf("a", min = 5, currentReplenished = 2)
        val ok = stockOf("b", min = 5, currentReplenished = 10)
        val list = ShoppingList(listOf(low, ok))

        val result = list.itemsToBuy()
        result.size shouldBe 1
        result[0].stock shouldBe low
        result[0].shortage shouldBe 3
    }
}
