package net.brightroom.mindstock.domain.model.inventory.stock

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.exception.CannotArchiveWithStockException
import net.brightroom.mindstock.domain.exception.InsufficientStockException
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.barcode.Barcode
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
    fun 補充後に消費すると純量が追跡される() {
        val stock =
            emptyStock()
                .replenish(Quantity(5), OccurredAt.now(), actor(), Note(""))
                .consume(Quantity(2), OccurredAt.now(), actor(), Note(""))
        stock.currentQuantity() shouldBe 3
    }

    @Test
    fun 在庫を超える消費は拒否される() {
        val stock = emptyStock().replenish(Quantity(2), OccurredAt.now(), actor(), Note(""))
        shouldThrow<InsufficientStockException> {
            stock.consume(Quantity(3), OccurredAt.now(), actor(), Note(""))
        }
    }

    @Test
    fun ステータスは現在の純量を反映する() {
        val stock = emptyStock(minimum = 3).replenish(Quantity(2), OccurredAt.now(), actor(), Note(""))
        stock.status() shouldBe StockStatus.残りわずか
    }

    @Test
    fun 在庫が残っていればアーカイブは拒否される() {
        val stock = emptyStock().replenish(Quantity(1), OccurredAt.now(), actor(), Note(""))
        shouldThrow<CannotArchiveWithStockException> { stock.archive() }
    }

    @Test
    fun 在庫がゼロならアーカイブできる() {
        emptyStock().archive().product.status shouldBe ProductStatus.アーカイブ済
    }

    @Test
    fun 訂正は対象変動の数量を上書きする() {
        // 補充5、消費3(id=10)→ 現在2。消費を1に訂正 → +5 - 1 = 4
        val stock = stockWith(persistedReplenishment(1, 5), persistedConsumption(10, 3))
        stock.currentQuantity() shouldBe 2
        val corrected = stock.correct(MovementId(10), Quantity(1), Reason("数え直し"), actor(), OccurredAt.now())
        corrected.currentQuantity() shouldBe 4
    }

    @Test
    fun 総量が負になる訂正は拒否される() {
        // 補充2、消費2(id=10)→ 現在0。消費を5に訂正 → +2 - 5 = -3 で拒否
        val stock = stockWith(persistedReplenishment(1, 2), persistedConsumption(10, 2))
        shouldThrow<InsufficientStockException> {
            stock.correct(MovementId(10), Quantity(5), Reason("入れ過ぎ"), actor(), OccurredAt.now())
        }
    }

    @Test
    fun 存在しない変動への訂正は例外を投げる() {
        val stock = stockWith(persistedReplenishment(1, 2))
        shouldThrow<ResourceNotFoundException> {
            stock.correct(MovementId(999), Quantity(1), Reason("対象なし"), actor(), OccurredAt.now())
        }
    }

    @Test
    fun 補充を下方訂正して純量が負になる場合も拒否される() {
        // 補充5(id=10) − 消費4 → 現在1。補充を2に訂正 → +2 − 4 = −2 で拒否
        val stock = stockWith(persistedReplenishment(10, 5), persistedConsumption(20, 4))
        shouldThrow<InsufficientStockException> {
            stock.correct(MovementId(10), Quantity(2), Reason("数え直し"), actor(), OccurredAt.now())
        }
    }
}
