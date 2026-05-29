package net.brightroom.mindstock.domain.model.catalog

import kotlinx.serialization.Serializable

/**
 * カタログ商品の集合。検索結果等で使う。
 */
@Serializable
data class CatalogItems(
    val list: List<CatalogItem>,
)
