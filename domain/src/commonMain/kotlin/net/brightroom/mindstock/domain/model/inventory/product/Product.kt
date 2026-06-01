package net.brightroom.mindstock.domain.model.inventory.product

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.inventory.product.image.ProductImage
import net.brightroom.mindstock.domain.model.inventory.product.setting.StockingPolicy

@Serializable
data class Product(
    val id: ProductId,
    val catalogItem: CatalogItem,
    val setting: StockingPolicy,
    val image: ProductImage,
    val status: ProductStatus,
) {
    fun archive(): Product = Product(id, catalogItem, setting, image, ProductStatus.アーカイブ済)

    fun unarchive(): Product = Product(id, catalogItem, setting, image, ProductStatus.採用中)
}
