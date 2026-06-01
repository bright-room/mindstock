package net.brightroom.mindstock.domain.model.inventory.stock.movement

import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import kotlin.test.Test
import kotlin.time.Instant

class StockMovementsTest {
    private fun actor() = Resident(ResidentId.create(), Profile(DisplayName("おや")))

    private fun at(epochSeconds: Long) = OccurredAt(Instant.fromEpochSeconds(epochSeconds))

    private fun persisted(id: Long) = MovementIdentity.Persisted(MovementId(id))

    private fun replenish(
        id: Long,
        n: Int,
    ) = Replenishment(persisted(id), Quantity(n), at(0), actor(), Note(""))

    private fun consume(
        id: Long,
        n: Int,
    ) = Consumption(persisted(id), Quantity(n), at(0), actor(), Note(""))

    private fun correction(
        target: Long,
        n: Int,
        atSeconds: Long,
    ) = Correction(MovementIdentity.Pending, Quantity(n), at(atSeconds), actor(), Note(""), MovementId(target), Reason("数え直し"))

    @Test
    fun empty_is_zero() {
        StockMovements(emptyList()).netQuantity() shouldBe 0
    }

    @Test
    fun sums_base_without_corrections() {
        // +2 +3 -1 = 4
        StockMovements(listOf(replenish(1, 2), replenish(2, 3), consume(3, 1))).netQuantity() shouldBe 4
    }

    @Test
    fun correction_overwrites_consumption_keeping_minus_sign() {
        // 補充2 +(消費1 → 訂正で2) = +2 - 2 = 0
        val movements = StockMovements(listOf(replenish(1, 2), consume(2, 1), correction(target = 2, n = 2, atSeconds = 100)))
        movements.netQuantity() shouldBe 0
    }

    @Test
    fun correction_overwrites_replenishment_keeping_plus_sign() {
        // (補充5 → 訂正で2)+ 消費1 = +2 - 1 = 1（base が Replenishment の + 符号を継承）
        val movements = StockMovements(listOf(replenish(1, 5), consume(2, 1), correction(target = 1, n = 2, atSeconds = 100)))
        movements.netQuantity() shouldBe 1
    }

    @Test
    fun latest_correction_wins() {
        // 補充10、消費1(id=2)を 3→5 と二度訂正 → 最新 5 を採用 → 10 - 5 = 5
        val movements =
            StockMovements(
                listOf(
                    replenish(1, 10),
                    consume(2, 1),
                    correction(target = 2, n = 3, atSeconds = 100),
                    correction(target = 2, n = 5, atSeconds = 200),
                ),
            )
        movements.netQuantity() shouldBe 5
    }

    @Test
    fun size_counts_all_movements() {
        StockMovements(listOf(replenish(1, 2), consume(2, 1))).size() shouldBe 2
    }
}
