@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas

import net.brightroom.mindstock.domain.model.inventory.product.ProductStatus
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime

object ProductRevisionsTable : Table("product_revisions") {
    val id = long("id").autoIncrement()
    override val primaryKey = PrimaryKey(id)

    val productId = reference("product_id", ProductsTable.id, onDelete = ReferenceOption.RESTRICT)
    val unit = varchar("unit", 10)
    val minimumStock = integer("minimum_stock")
    val imageRef = varchar("image_ref", 512).nullable() // null = ProductImage.None
    val status = enumerationByName("status", 20, ProductStatus::class)
    val recordedAt = datetime("recorded_at").defaultExpression(CurrentDateTime)

    init {
        index(false, productId, id)
    }
}
