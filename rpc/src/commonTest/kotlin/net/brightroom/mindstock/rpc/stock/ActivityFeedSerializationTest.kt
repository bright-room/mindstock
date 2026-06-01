package net.brightroom.mindstock.rpc.stock

import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import net.brightroom.mindstock.domain.model.catalog.barcode.Barcode
import net.brightroom.mindstock.domain.model.catalog.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.content.CatalogContent
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemUnit
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.origin.CatalogOrigin
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.ProductStatus
import net.brightroom.mindstock.domain.model.inventory.product.image.ImageRef
import net.brightroom.mindstock.domain.model.inventory.product.image.ProductImage
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.product.setting.StockingPolicy
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
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
                catalogItem =
                    CatalogItem(
                        id = CatalogItemId.create(),
                        content = CatalogContent(CatalogItemName("シャンプー"), CatalogItemUnit("本")),
                        barcode = Barcode.Linked(Jan("4901234567894")),
                        origin = CatalogOrigin.世帯独自,
                    ),
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
                catalogItem =
                    CatalogItem(
                        id = CatalogItemId.create(),
                        content = CatalogContent(CatalogItemName("牛乳"), CatalogItemUnit("本")),
                        barcode = Barcode.Linked(Jan("4901234567894")),
                        origin = CatalogOrigin.世帯独自,
                    ),
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
}
