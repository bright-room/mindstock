package net.brightroom.mindstock.rpc.stock

import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.ProductStatus
import net.brightroom.mindstock.domain.model.inventory.product.image.ImageRef
import net.brightroom.mindstock.domain.model.inventory.product.image.ProductImage
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.product.setting.StockingPolicy
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementIdentity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import net.brightroom.mindstock.extensions.kotlinx.serialization.KrpcJson
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.test.Test

class ActivityFeedSerializationTest {
    @Test
    fun ProductImage_Stored_を含む_Product_を_RpcResult_Ok_として往復できる() {
        val product =
            Product(
                id = ProductId.create(),
                name = ProductName("シャンプー"),
                barcode = Barcode.Linked(Jan("4901234567894")),
                setting = StockingPolicy(ProductUnit("本"), MinimumStock(2)),
                image = ProductImage.Stored(ImageRef("https://example.com/images/shampoo.jpg")),
                status = ProductStatus.採用中,
            )
        val ok: RpcResult<Product, RpcError> = RpcResult.Ok(product)
        val back = KrpcJson.decodeFromString<RpcResult<Product, RpcError>>(KrpcJson.encodeToString(ok))
        back shouldBe ok
    }

    @Test
    fun StockMovement_と_MovementIdentity_を含む_ActivityFeed_を_RpcResult_Ok_として往復できる() {
        val product =
            Product(
                id = ProductId.create(),
                name = ProductName("牛乳"),
                barcode = Barcode.Linked(Jan("4901234567894")),
                setting = StockingPolicy(ProductUnit("本"), MinimumStock(1)),
                image = ProductImage.None,
                status = ProductStatus.採用中,
            )
        val actor =
            Resident(
                id = ResidentId.create(),
                profile = Profile(DisplayName("たろう")),
            )
        val movement =
            StockMovement.Replenishment(
                identity = MovementIdentity.Pending,
                quantity = Quantity(3),
                occurredAt = OccurredAt.now(),
                actor = actor,
                note = Note("補充テスト"),
            )
        val feed = ActivityFeed(list = listOf(ActivityEntry(product = product, movement = movement)))
        val ok: RpcResult<ActivityFeed, RpcError> = RpcResult.Ok(feed)
        val back = KrpcJson.decodeFromString<RpcResult<ActivityFeed, RpcError>>(KrpcJson.encodeToString(ok))
        back shouldBe ok
    }

    @Test
    fun MovementIdentity_Persisted_を含む_ActivityFeed_を_RpcResult_Ok_として往復できる() {
        val product =
            Product(
                id = ProductId.create(),
                name = ProductName("洗剤"),
                barcode = Barcode.Linked(Jan("4901234567894")),
                setting = StockingPolicy(ProductUnit("個"), MinimumStock(2)),
                image = ProductImage.None,
                status = ProductStatus.採用中,
            )
        val actor =
            Resident(
                id = ResidentId.create(),
                profile = Profile(DisplayName("はなこ")),
            )
        val movement =
            StockMovement.Replenishment(
                identity = MovementIdentity.Persisted(MovementId(42L)),
                quantity = Quantity(1),
                occurredAt = OccurredAt(LocalDateTime(2026, 1, 1, 12, 0)),
                actor = actor,
                note = Note("DB 行から復元した本番経路"),
            )
        val feed = ActivityFeed(list = listOf(ActivityEntry(product = product, movement = movement)))
        val ok: RpcResult<ActivityFeed, RpcError> = RpcResult.Ok(feed)
        val back = KrpcJson.decodeFromString<RpcResult<ActivityFeed, RpcError>>(KrpcJson.encodeToString(ok))
        back shouldBe ok
    }
}
