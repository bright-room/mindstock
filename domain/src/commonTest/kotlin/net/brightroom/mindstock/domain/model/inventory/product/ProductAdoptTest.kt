package net.brightroom.mindstock.domain.model.inventory.product

import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.inventory.product.image.ProductImage
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import kotlin.test.Test

class ProductAdoptTest {
    @Test
    fun `catalog の名前とJANをコピーしLinkedバーコードの採用中商品を生成する`() {
        val catalogItem =
            CatalogItem(
                id = CatalogItemId.create(),
                jan = Jan("4901234567894"),
                name = CatalogItemName("明治おいしい牛乳"),
            )

        val product = Product.adopt(catalogItem, ProductUnit("本"), MinimumStock(2))

        product.name() shouldBe "明治おいしい牛乳"
        product.barcode shouldBe Barcode.Linked(Jan("4901234567894"))
        product.status shouldBe ProductStatus.採用中
        product.image shouldBe ProductImage.None
    }
}
