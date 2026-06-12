package net.brightroom.mindstock.domain.model.inventory.stock

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.ResidentProfile
import kotlin.test.Test

class StockLatestMovementTest {
    private val actor = Resident(ResidentId.create(), ResidentProfile(DisplayName("たろう")))
    private val product = Product.custom(ProductName("水"), Barcode.Unlinked, ProductUnit("本"), MinimumStock(1))
    private val stock = Stock(product, StockMovements(emptyList()))

    @Test
    fun 補充後の_latestMovement_は今追加した補充を返す() {
        val replenished = stock.replenish(Quantity(3), OccurredAt.now(), actor, Note(""))
        val latest = replenished.latestMovement()
        latest.shouldBeInstanceOf<StockMovement.Replenishment>()
        latest.quantity() shouldBe 3
    }
}
