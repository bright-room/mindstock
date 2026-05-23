package net.brightroom.mindstock.domain.model.stock

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.stock.consumption.Consumption
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
class StockTest {
    private val user =
        User(
            id = UserId(Uuid.generateV7()),
            authIdentity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-1")),
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
            minimumStock = min?.let { MinimumStock(it) },
            archived = false,
        )

    private val now = Instant.parse("2026-05-24T10:00:00Z")

    private fun occurred(
        year: Int = 2026,
        day: Int = 1,
    ) = OccurredAt(Instant.parse("$year-05-0${day}T10:00:00Z"), now)

    private fun replenish(
        product: Product,
        qty: Int,
    ) = Replenishment(
        product = product,
        quantity = Quantity(qty),
        occurredAt = occurred(),
        actor = user,
        note = Note(""),
    )

    private fun consume(
        product: Product,
        qty: Int,
    ) = Consumption(
        product = product,
        quantity = Quantity(qty),
        occurredAt = occurred(),
        actor = user,
        note = Note(""),
    )

    @Test
    fun `currentQuantity is replenishments minus consumptions when no corrections`() {
        val p = productWithMin(null)
        val stock =
            Stock(
                product = p,
                replenishments = Replenishments(listOf(replenish(p, 5), replenish(p, 3))),
                consumptions = Consumptions(listOf(consume(p, 2))),
                replenishmentCorrections = emptyList(),
                consumptionCorrections = emptyList(),
            )
        stock.currentQuantity() shouldBe 6
    }

    @Test
    fun `needsReplenishment is true when current quantity is below minimum`() {
        val p = productWithMin(5)
        val stock =
            Stock(
                product = p,
                replenishments = Replenishments(listOf(replenish(p, 3))),
                consumptions = Consumptions(emptyList()),
                replenishmentCorrections = emptyList(),
                consumptionCorrections = emptyList(),
            )
        stock.needsReplenishment().shouldBeTrue()
        stock.shortage() shouldBe 2
    }

    @Test
    fun `needsReplenishment is false when minimumStock is null`() {
        val p = productWithMin(null)
        val stock =
            Stock(
                product = p,
                replenishments = Replenishments(emptyList()),
                consumptions = Consumptions(emptyList()),
                replenishmentCorrections = emptyList(),
                consumptionCorrections = emptyList(),
            )
        stock.needsReplenishment().shouldBeFalse()
    }
}
