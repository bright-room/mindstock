@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.product

import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.image.ImageRef
import net.brightroom.mindstock.domain.model.inventory.product.image.ProductImage
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.product.setting.StockingPolicy
import net.brightroom.mindstock.infrastructure.datasource.schemas.ProductBarcodesTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.ProductRevisionsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.ProductsTable
import org.jetbrains.exposed.v1.core.QueryAlias
import org.jetbrains.exposed.v1.core.ResultRow

/**
 * products 行 + 最新 product_revisions 行(revSub alias)から Product を組み立てる。
 * product_barcodes LEFT JOIN の jan(LEFT JOIN 未マッチで null)→ Barcode、image_ref(nullable)→ ProductImage を導出。
 */
internal fun ResultRow.toProduct(revSub: QueryAlias): Product =
    Product(
        id = ProductId(this[ProductsTable.id]),
        name = ProductName(this[ProductsTable.name]),
        barcode = this.getOrNull(ProductBarcodesTable.jan)?.let { Barcode.Linked(Jan(it)) } ?: Barcode.Unlinked,
        setting =
            StockingPolicy(
                ProductUnit(this[revSub[ProductRevisionsTable.unit]]),
                MinimumStock(this[revSub[ProductRevisionsTable.minimumStock]]),
            ),
        image =
            this[revSub[ProductRevisionsTable.imageRef]]
                ?.let { ProductImage.Stored(ImageRef(it)) } ?: ProductImage.None,
        status = this[revSub[ProductRevisionsTable.status]],
    )

/** Product → jan 列値(Barcode を潰す)。 */
internal fun Barcode.toJanColumn(): String? =
    when (this) {
        is Barcode.Linked -> jan()
        Barcode.Unlinked -> null
    }

/** Product → image_ref 列値。 */
internal fun ProductImage.toImageRefColumn(): String? =
    when (this) {
        is ProductImage.Stored -> ref()
        ProductImage.None -> null
    }
