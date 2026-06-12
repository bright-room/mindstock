package net.brightroom.mindstock.application.service.catalog

import net.brightroom.mindstock.application.repository.catalog.CatalogRegisterRepository
import net.brightroom.mindstock.application.repository.catalog.CatalogRepository
import net.brightroom.mindstock.application.repository.catalog.ExternalProductRepository
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.SearchLimit
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItems

class CatalogService(
    private val catalogRepository: CatalogRepository,
    private val catalogRegisterRepository: CatalogRegisterRepository,
    private val externalProductRepository: ExternalProductRepository,
) {
    fun search(
        name: CatalogItemName,
        limit: SearchLimit,
    ): CatalogItems = catalogRepository.search(name, limit)

    /** 内部用(adopt の item 解決)。 */
    fun findById(catalogItemId: CatalogItemId): CatalogItem = catalogRepository.findById(catalogItemId)

    /** UC11,12: master 照合 → 未存在で外部 API → hit で cache 保存 → どちらも無ければ NotFound(素通し)。 */
    fun lookupByJan(jan: Jan): CatalogItem =
        try {
            catalogRepository.findByJan(jan)
        } catch (e: ResourceNotFoundException) {
            val received = externalProductRepository.findByJan(jan)
            catalogRegisterRepository.register(received)
            received
        }
}
