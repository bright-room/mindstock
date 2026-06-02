package net.brightroom.mindstock.rpc.product

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemUnit
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock

/**
 * マスタに無い商品をその場で追加(UC13)。複合パラメータを 1 つにまとめた Request。
 * `barcode` で JAN 任意を表現(`Barcode.Unlinked` = JAN 無し / `Barcode.Linked(jan)` = JAN 有り)。
 * 採用時の `ProductUnit` は backend が `unit`(CatalogItemUnit)から構築する。
 */
@Serializable
data class AddCustomProductRequest(
    val name: CatalogItemName,
    val unit: CatalogItemUnit,
    val barcode: Barcode,
    val minimumStock: MinimumStock,
)
