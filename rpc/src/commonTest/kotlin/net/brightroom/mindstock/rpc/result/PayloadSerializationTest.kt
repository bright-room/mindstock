package net.brightroom.mindstock.rpc.result

import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.ProductStatus
import net.brightroom.mindstock.domain.model.inventory.product.image.ProductImage
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.product.setting.StockingPolicy
import net.brightroom.mindstock.extensions.kotlinx.serialization.KrpcJson
import kotlin.test.Test

class PayloadSerializationTest {
    @Test
    fun `Barcode_Unlinked_と_Linked_を_RpcResult_Ok_として往復できる`() {
        val unlinked: Barcode = Barcode.Unlinked
        val okUnlinked: RpcResult<Barcode, RpcError> = RpcResult.Ok(unlinked)
        val backUnlinked = KrpcJson.decodeFromString<RpcResult<Barcode, RpcError>>(KrpcJson.encodeToString(okUnlinked))
        backUnlinked shouldBe okUnlinked

        val linked: Barcode = Barcode.Linked(Jan("4901234567894"))
        val okLinked: RpcResult<Barcode, RpcError> = RpcResult.Ok(linked)
        val backLinked = KrpcJson.decodeFromString<RpcResult<Barcode, RpcError>>(KrpcJson.encodeToString(okLinked))
        backLinked shouldBe okLinked
    }

    @Test
    fun sealed_と_value_class_を含む_Product_を_RpcResult_Ok_として往復できる() {
        val product =
            Product(
                id = ProductId.create(),
                name = ProductName("洗剤"),
                barcode = Barcode.Linked(Jan("4901234567894")),
                setting = StockingPolicy(ProductUnit("個"), MinimumStock(1)),
                image = ProductImage.None,
                status = ProductStatus.採用中,
            )
        val ok: RpcResult<Product, RpcError> = RpcResult.Ok(product)
        val back = KrpcJson.decodeFromString<RpcResult<Product, RpcError>>(KrpcJson.encodeToString(ok))
        back shouldBe ok
    }
}
