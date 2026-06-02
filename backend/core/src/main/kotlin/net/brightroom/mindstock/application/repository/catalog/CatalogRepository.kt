package net.brightroom.mindstock.application.repository.catalog

import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItems

interface CatalogRepository {
    /** 名前 LIKE 検索(空なら空 CatalogItems)。 */
    fun search(
        query: String,
        limit: Int,
    ): CatalogItems

    /** JAN 照会(不在は ResourceNotFoundException → P5 で外部 API fallback)。 */
    fun findByJan(jan: Jan): CatalogItem

    fun findById(id: CatalogItemId): CatalogItem
}
