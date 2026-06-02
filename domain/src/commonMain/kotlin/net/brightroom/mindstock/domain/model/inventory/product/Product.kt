package net.brightroom.mindstock.domain.model.inventory.product

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.inventory.product.image.ProductImage
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.product.setting.StockingPolicy

@Serializable
data class Product(
    val id: ProductId,
    val name: ProductName,
    val barcode: Barcode,
    val setting: StockingPolicy,
    val image: ProductImage,
    val status: ProductStatus,
) {
    fun archive(): Product = Product(id, name, barcode, setting, image, ProductStatus.アーカイブ済)

    fun unarchive(): Product = Product(id, name, barcode, setting, image, ProductStatus.採用中)

    companion object {
        fun adopt(
            catalogItem: CatalogItem,
            unit: ProductUnit,
            minimumStock: MinimumStock,
        ): Product =
            Product(
                id = ProductId.create(),
                name = ProductName(catalogItem.name()),
                barcode = Barcode.Linked(catalogItem.jan),
                setting = StockingPolicy(unit, minimumStock),
                image = ProductImage.None,
                status = ProductStatus.採用中,
            )

        fun custom(
            name: ProductName,
            barcode: Barcode,
            unit: ProductUnit,
            minimumStock: MinimumStock,
        ): Product =
            Product(
                id = ProductId.create(),
                name = name,
                barcode = barcode,
                setting = StockingPolicy(unit, minimumStock),
                image = ProductImage.None,
                status = ProductStatus.採用中,
            )
    }
}
