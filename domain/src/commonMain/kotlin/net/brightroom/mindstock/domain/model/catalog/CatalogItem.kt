package net.brightroom.mindstock.domain.model.catalog

/**
 * カタログ商品(全世帯で共有される商品概念)。
 *
 * 名前と単位は現在値。リビジョン履歴は Repository が hydrate するときに
 * 最新を取って組み立てる(DB の catalog_item_revisions テーブルは継続使用)。
 */
data class CatalogItem(
    val id: CatalogItemId,
    val name: CatalogItemName,
    val unit: CatalogItemUnit,
)
