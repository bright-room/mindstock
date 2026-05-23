package net.brightroom.mindstock.domain.model.catalog

/**
 * カタログ商品の集合。検索結果等で使う。
 */
class CatalogItems(private val list: List<CatalogItem>) {
    fun asList(): List<CatalogItem> = list.toList()
    val size: Int get() = list.size
}
