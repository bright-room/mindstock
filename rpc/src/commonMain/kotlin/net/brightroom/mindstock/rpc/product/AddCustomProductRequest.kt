package net.brightroom.mindstock.rpc.product

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit

/**
 * マスタに無い商品をその場で追加(UC13)。複合パラメータを 1 つにまとめた Request。
 * `barcode` で JAN 任意を表現(`Barcode.Unlinked` = JAN 無し / `Barcode.Linked(jan)` = JAN 有り)。
 */
@Serializable
data class AddCustomProductRequest(
    val name: ProductName,
    val unit: ProductUnit,
    val barcode: Barcode,
    val minimumStock: MinimumStock,
)
