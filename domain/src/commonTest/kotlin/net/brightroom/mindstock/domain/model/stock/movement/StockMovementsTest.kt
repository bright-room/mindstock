package net.brightroom.mindstock.domain.model.stock.movement

import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
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
class StockMovementsTest {
    private val now = Instant.parse("2026-05-24T10:00:00Z")
    private val user =
        User(
            id = UserId(Uuid.generateV7()),
            authIdentity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-1")),
            displayName = DisplayName("alice"),
        )
    private val product =
        Product(
            id = ProductId(Uuid.generateV7()),
            catalogItem =
                CatalogItem(
                    id = CatalogItemId(Uuid.generateV7()),
                    name = CatalogItemName("ハンドソープ"),
                    unit = CatalogItemUnit("本"),
                ),
            minimumStock = null,
            archived = false,
        )

    private fun occurred() =
        OccurredAt(
            LocalDateTime(2026, 5, 1, 10, 0).toInstant(TimeZone.UTC),
            now,
        )

    private fun replenish(qty: Int) = Replenishment(product, Quantity(qty), occurred(), user, Note(""))

    private fun consume(qty: Int) = Consumption(product, Quantity(qty), occurred(), user, Note(""))

    @Test
    fun `netQuantity is zero for empty movements`() {
        StockMovements(emptyList()).netQuantity() shouldBe 0
    }

    @Test
    fun `netQuantity sums replenishments as positive`() {
        StockMovements(listOf(replenish(5), replenish(3))).netQuantity() shouldBe 8
    }

    @Test
    fun `netQuantity sums consumptions as negative`() {
        StockMovements(listOf(consume(2), consume(1))).netQuantity() shouldBe -3
    }

    @Test
    fun `netQuantity mixes replenishments and consumptions`() {
        StockMovements(listOf(replenish(10), consume(3), replenish(2), consume(4)))
            .netQuantity() shouldBe 5
    }
}
