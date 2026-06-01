package net.brightroom.mindstock.domain.model.inventory.stock

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.exception.CannotArchiveWithStockException
import net.brightroom.mindstock.domain.exception.InsufficientStockException
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.catalog.barcode.Barcode
import net.brightroom.mindstock.domain.model.catalog.content.CatalogContent
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemUnit
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.origin.CatalogOrigin
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.ProductStatus
import net.brightroom.mindstock.domain.model.inventory.product.image.ProductImage
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.product.setting.StockingPolicy
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Consumption
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementIdentity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Reason
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Replenishment
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import kotlin.test.Test

class StockTest {
    private fun actor() = Resident(ResidentId.create(), Profile(DisplayName("おや")))

    private fun product(minimum: Int = 1) =
        Product(
            id = ProductId.create(),
            catalogItem =
                CatalogItem(
                    id = CatalogItemId.create(),
                    content = CatalogContent(CatalogItemName("米"), CatalogItemUnit("袋")),
                    barcode = Barcode.Unlinked,
                    origin = CatalogOrigin.世帯独自,
                ),
            setting = StockingPolicy(ProductUnit("袋"), MinimumStock(minimum)),
            image = ProductImage.None,
            status = ProductStatus.採用中,
        )

    private fun emptyStock(minimum: Int = 1) = Stock(product(minimum), StockMovements(emptyList()))

    // 訂正テスト用に「永続化済み(Persisted)movement」を直接組んだ Stock を作る
    private fun persistedReplenishment(
        id: Long,
        n: Int,
    ) = Replenishment(MovementIdentity.Persisted(MovementId(id)), Quantity(n), OccurredAt.now(), actor(), Note(""))

    private fun persistedConsumption(
        id: Long,
        n: Int,
    ) = Consumption(MovementIdentity.Persisted(MovementId(id)), Quantity(n), OccurredAt.now(), actor(), Note(""))

    private fun stockWith(
        vararg movements: StockMovement,
        minimum: Int = 1,
    ) = Stock(product(minimum), StockMovements(movements.toList()))

    @Test
    fun replenish_then_consume_tracks_quantity() {
        val stock =
            emptyStock()
                .replenish(Quantity(5), OccurredAt.now(), actor(), Note(""))
                .consume(Quantity(2), OccurredAt.now(), actor(), Note(""))
        stock.currentQuantity() shouldBe 3
    }

    @Test
    fun consume_beyond_stock_is_rejected() {
        val stock = emptyStock().replenish(Quantity(2), OccurredAt.now(), actor(), Note(""))
        shouldThrow<InsufficientStockException> {
            stock.consume(Quantity(3), OccurredAt.now(), actor(), Note(""))
        }
    }

    @Test
    fun status_reflects_current_quantity() {
        val stock = emptyStock(minimum = 3).replenish(Quantity(2), OccurredAt.now(), actor(), Note(""))
        stock.status() shouldBe StockStatus.残りわずか
    }

    @Test
    fun archive_is_rejected_when_stock_remains() {
        val stock = emptyStock().replenish(Quantity(1), OccurredAt.now(), actor(), Note(""))
        shouldThrow<CannotArchiveWithStockException> { stock.archive() }
    }

    @Test
    fun archive_succeeds_when_empty() {
        emptyStock().archive().product.status shouldBe ProductStatus.アーカイブ済
    }

    @Test
    fun correct_overwrites_target_quantity() {
        // 補充5、消費3(id=10)→ 現在2。消費を1に訂正 → +5 - 1 = 4
        val stock = stockWith(persistedReplenishment(1, 5), persistedConsumption(10, 3))
        stock.currentQuantity() shouldBe 2
        val corrected = stock.correct(MovementId(10), Quantity(1), Reason("数え直し"), actor(), OccurredAt.now())
        corrected.currentQuantity() shouldBe 4
    }

    @Test
    fun correct_to_negative_total_is_rejected() {
        // 補充2、消費2(id=10)→ 現在0。消費を5に訂正 → +2 - 5 = -3 で拒否
        val stock = stockWith(persistedReplenishment(1, 2), persistedConsumption(10, 2))
        shouldThrow<InsufficientStockException> {
            stock.correct(MovementId(10), Quantity(5), Reason("入れ過ぎ"), actor(), OccurredAt.now())
        }
    }

    @Test
    fun correct_unknown_target_is_not_found() {
        val stock = stockWith(persistedReplenishment(1, 2))
        shouldThrow<ResourceNotFoundException> {
            stock.correct(MovementId(999), Quantity(1), Reason("対象なし"), actor(), OccurredAt.now())
        }
    }
}
