@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

/** product の barcode(Barcode.Linked のときのみ行が存在。行無し = Unlinked)。products.jan の nullable を排した side-table。 */
object ProductBarcodesTable : Table("product_barcodes") {
    val productId = reference("product_id", ProductsTable.id, onDelete = ReferenceOption.RESTRICT)
    val jan = varchar("jan", 13)
    override val primaryKey = PrimaryKey(productId)
}
