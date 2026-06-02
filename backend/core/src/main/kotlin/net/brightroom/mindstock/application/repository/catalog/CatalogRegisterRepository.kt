package net.brightroom.mindstock.application.repository.catalog

import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem

interface CatalogRegisterRepository {
    /** 外部 API 取得品を catalog_items に保存(キャッシュ)。 */
    fun register(catalogItem: CatalogItem): CatalogItem
}
