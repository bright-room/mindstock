package net.brightroom.mindstock.domain.model.product

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.catalog.CatalogItem

/**
 * 世帯固有の商品インスタンス。CatalogItem を世帯で「採用」したもの。
 *
 * 最低在庫値とアーカイブ状態を集約スナップショットとして持つ。
 * householdId は domain には出さない(Household 経由でアクセス前提)。
 */
@Serializable
data class Product(
    val id: ProductId,
    val catalogItem: CatalogItem,
    val minimumStock: MinimumStock,
    val archived: Boolean,
)
