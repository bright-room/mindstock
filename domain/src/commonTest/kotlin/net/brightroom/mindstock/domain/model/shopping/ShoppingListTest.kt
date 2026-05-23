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
import net.brightroom.mindstock.domain.model.stock.consumption.Consumptions
import net.brightroom.mindstock.domain.model.stock.replenishment.Replenishment
import net.brightroom.mindstock.domain.model.stock.replenishment.Replenishments
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import kotlin.test.Test
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ShoppingListTest {
    private val user =
        User(
            UserId(Uuid.generateV7()),
            AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub")),
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
                minimumStock = MinimumStock(min),
                archived = false,
            )
        val r =
            if (currentReplenished > 0) {
                listOf(
                    Replenishment(
                        product = product,
                        quantity = Quantity(currentReplenished),
                        occurredAt = OccurredAt(Instant.parse("2026-05-23T10:00:00Z"), now),
                        actor = user,
                        note = Note(""),
                    ),
                )
            } else {
                emptyList()
            }
        return Stock(
            product = product,
            replenishments = Replenishments(r),
            consumptions = Consumptions(emptyList()),
            replenishmentCorrections = emptyList(),
            consumptionCorrections = emptyList(),
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
