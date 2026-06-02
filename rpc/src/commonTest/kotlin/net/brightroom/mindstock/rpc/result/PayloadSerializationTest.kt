package net.brightroom.mindstock.rpc.result

import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.barcode.Jan
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
import net.brightroom.mindstock.extensions.kotlinx.serialization.KrpcJson
import kotlin.test.Test

class PayloadSerializationTest {
    @Test
    fun sealed_と_value_class_を含む_Product_を_RpcResult_Ok_として往復できる() {
        val product =
            Product(
                id = ProductId.create(),
                catalogItem =
                    CatalogItem(
                        id = CatalogItemId.create(),
                        content = CatalogContent(CatalogItemName("洗剤"), CatalogItemUnit("個")),
                        barcode = Barcode.Linked(Jan("4901234567894")),
                        origin = CatalogOrigin.世帯独自,
                    ),
                setting = StockingPolicy(ProductUnit("個"), MinimumStock(1)),
                image = ProductImage.None,
                status = ProductStatus.採用中,
            )
        val ok: RpcResult<Product, RpcError> = RpcResult.Ok(product)
        val back = KrpcJson.decodeFromString<RpcResult<Product, RpcError>>(KrpcJson.encodeToString(ok))
        back shouldBe ok
    }
}
