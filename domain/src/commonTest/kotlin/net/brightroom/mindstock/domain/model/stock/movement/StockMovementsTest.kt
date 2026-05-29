package net.brightroom.mindstock.domain.model.stock.movement

import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import net.brightroom.mindstock.domain.model.stock.movement.Note
import net.brightroom.mindstock.domain.model.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.stock.movement.Quantity
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile
import kotlin.test.Test
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class StockMovementsTest {
    private val profile =
        Profile(
            userId = UserId(Uuid.generateV7()),
            displayName = DisplayName("alice"),
        )

    private fun occurred() = OccurredAt(LocalDateTime(2026, 5, 1, 10, 0).toInstant(TimeZone.UTC))

    private fun replenish(qty: Int) = Replenishment(Quantity(qty), occurred(), profile, Note(""))

    private fun consume(qty: Int) = Consumption(Quantity(qty), occurred(), profile, Note(""))

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
